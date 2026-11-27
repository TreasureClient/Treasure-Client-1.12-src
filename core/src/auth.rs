//! Microsoft sign-in (the "device code" way: the launcher shows a short code, you type it at
//! microsoft.com/link in your browser, so the launcher never sees your password).
//!
//! Microsoft -> Xbox Live -> Minecraft services, then a check that the account owns Minecraft.
//! Only works once Mojang has approved Treasure Launcher's Azure app; until then CLIENT_ID is
//! empty and sign-in says so. Tokens are kept in accounts.json in the data folder (only your
//! user can read it) and are never shown in the app or written to logs.

use std::fs;
use std::io::Write;

use anyhow::{bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

use crate::launch::Auth;
use crate::{net, paths, util};

/// The Azure app's client ID (not a secret). Set at build time with TREASURE_MS_CLIENT_ID, or here.
pub const CLIENT_ID: &str = match option_env!("TREASURE_MS_CLIENT_ID") {
    Some(s) => s,
    None => "",
};

const SCOPE: &str = "XboxLive.signin offline_access";
const MS: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0";

#[derive(Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub uuid: String,
    pub name: String,
    refresh_token: String,
    mc_token: String,
    mc_expires: u64,
}

#[derive(Serialize, Deserialize, Default)]
struct Store {
    active: Option<String>,
    accounts: Vec<Account>,
}

/// What the app may know about an account (no tokens).
#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct AccountInfo {
    pub uuid: String,
    pub name: String,
    pub active: bool,
}

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct DeviceCode {
    pub user_code: String,
    pub verification_uri: String,
    pub interval: u64,
    pub expires_at: u64,
    #[serde(skip)]
    device_code: String,
}

pub fn available() -> bool {
    !CLIENT_ID.is_empty()
}

fn store_path() -> std::path::PathBuf {
    paths::root().join("accounts.json")
}

fn load() -> Store {
    util::read_json(&store_path()).unwrap_or_default()
}

fn save(s: &Store) -> Result<()> {
    let p = store_path();
    fs::create_dir_all(paths::root())?;
    let tmp = p.with_extension("json.tmp");
    let mut o = fs::OpenOptions::new();
    o.write(true).create(true).truncate(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        o.mode(0o600);
    }
    let mut f = o.open(&tmp)?;
    f.write_all(&serde_json::to_vec_pretty(s)?)?;
    drop(f);
    fs::rename(&tmp, &p)?;
    Ok(())
}

pub fn accounts() -> Vec<AccountInfo> {
    let s = load();
    s.accounts.iter().map(|a| AccountInfo { uuid: a.uuid.clone(), name: a.name.clone(), active: s.active.as_deref() == Some(&a.uuid) }).collect()
}

pub fn set_active(uuid: &str) -> Result<()> {
    let mut s = load();
    if !s.accounts.iter().any(|a| a.uuid == uuid) {
        bail!("that account isn't signed in");
    }
    s.active = Some(uuid.to_string());
    save(&s)
}

pub fn sign_out(uuid: &str) -> Result<()> {
    let mut s = load();
    s.accounts.retain(|a| a.uuid != uuid);
    if s.active.as_deref() == Some(uuid) {
        s.active = s.accounts.first().map(|a| a.uuid.clone());
    }
    save(&s)
}

/// Sends a form and reads the JSON answer whatever the status (Microsoft answers "not yet" with 400).
fn form(url: &str, fields: &[(&str, &str)]) -> Result<Value> {
    let mut r = net::agent().post(url).config().http_status_as_error(false).build().send_form(fields.iter().copied()).with_context(|| format!("couldn't reach {url}"))?;
    let t = r.body_mut().read_to_string()?;
    serde_json::from_str(&t).with_context(|| format!("unexpected answer from {url}"))
}

fn json_post(url: &str, body: &Value) -> Result<(u16, Value)> {
    let mut r = net::agent()
        .post(url)
        .config()
        .http_status_as_error(false)
        .build()
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .send(body.to_string().as_str())
        .with_context(|| format!("couldn't reach {url}"))?;
    let status = r.status().as_u16();
    let t = r.body_mut().read_to_string().unwrap_or_default();
    Ok((status, serde_json::from_str(&t).unwrap_or(Value::Null)))
}

fn bearer_get(url: &str, token: &str) -> Result<(u16, Value)> {
    let mut r = net::agent().get(url).config().http_status_as_error(false).build().header("Authorization", &format!("Bearer {token}")).call().with_context(|| format!("couldn't reach {url}"))?;
    let status = r.status().as_u16();
    let t = r.body_mut().read_to_string().unwrap_or_default();
    Ok((status, serde_json::from_str(&t).unwrap_or(Value::Null)))
}

/// Step 1: get a code to show. The person types it at `verification_uri`.
pub fn start() -> Result<DeviceCode> {
    if !available() {
        bail!("Microsoft sign-in turns on once Mojang approves Treasure Launcher. Until then, play through the official launcher.");
    }
    let v = form(&format!("{MS}/devicecode"), &[("client_id", CLIENT_ID), ("scope", SCOPE)])?;
    let s = |k: &str| v.get(k).and_then(|x| x.as_str()).unwrap_or("").to_string();
    if s("device_code").is_empty() {
        bail!("Microsoft didn't give a sign-in code ({})", s("error_description"));
    }
    Ok(DeviceCode {
        user_code: s("user_code"),
        verification_uri: s("verification_uri"),
        interval: v.get("interval").and_then(|x| x.as_u64()).unwrap_or(5).max(1),
        expires_at: util::now() + v.get("expires_in").and_then(|x| x.as_u64()).unwrap_or(900),
        device_code: s("device_code"),
    })
}

/// Step 2: call every `interval` seconds. None = they haven't finished in the browser yet.
pub fn poll(dc: &DeviceCode) -> Result<Option<AccountInfo>> {
    if util::now() > dc.expires_at {
        bail!("The sign-in code ran out of time. Try again.");
    }
    let v = form(
        &format!("{MS}/token"),
        &[("grant_type", "urn:ietf:params:oauth:grant-type:device_code"), ("client_id", CLIENT_ID), ("device_code", &dc.device_code)],
    )?;
    if let Some(err) = v.get("error").and_then(|x| x.as_str()) {
        return match err {
            "authorization_pending" | "slow_down" => Ok(None),
            "authorization_declined" => bail!("Sign-in was cancelled."),
            "expired_token" => bail!("The sign-in code ran out of time. Try again."),
            _ => bail!("Microsoft sign-in failed: {}", v.get("error_description").and_then(|x| x.as_str()).unwrap_or(err)),
        };
    }
    let ms = v.get("access_token").and_then(|x| x.as_str()).context("Microsoft sign-in gave no token")?;
    let refresh = v.get("refresh_token").and_then(|x| x.as_str()).unwrap_or("").to_string();
    let acc = finish(ms, refresh)?;
    let info = AccountInfo { uuid: acc.uuid.clone(), name: acc.name.clone(), active: true };
    let mut s = load();
    s.accounts.retain(|a| a.uuid != acc.uuid);
    s.active = Some(acc.uuid.clone());
    s.accounts.push(acc);
    save(&s)?;
    Ok(Some(info))
}

/// Microsoft token -> Xbox Live -> Minecraft token + profile.
fn finish(ms_token: &str, refresh_token: String) -> Result<Account> {
    let (st, xbl) = json_post(
        "https://user.auth.xboxlive.com/user/authenticate",
        &json!({"Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={ms_token}")}, "RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT"}),
    )?;
    let xbl_token = xbl.get("Token").and_then(|x| x.as_str()).with_context(|| format!("Xbox Live sign-in failed ({st})"))?;
    let (st, xsts) = json_post(
        "https://xsts.auth.xboxlive.com/xsts/authorize",
        &json!({"Properties": {"SandboxId": "RETAIL", "UserTokens": [xbl_token]}, "RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT"}),
    )?;
    if let Some(code) = xsts.get("XErr").and_then(|x| x.as_u64()) {
        bail!(match code {
            2148916233 => "This Microsoft account has no Xbox profile yet. Sign in once at minecraft.net, then try again.",
            2148916235 => "Xbox Live isn't available in your country.",
            2148916236 | 2148916237 => "This account needs an adult to finish setting it up at xbox.com.",
            2148916238 => "This account belongs to a child. An adult needs to add it to a Microsoft family first.",
            _ => "Xbox Live refused the sign-in.",
        });
    }
    let xsts_token = xsts.get("Token").and_then(|x| x.as_str()).with_context(|| format!("Xbox Live sign-in failed ({st})"))?;
    let uhs = xsts.pointer("/DisplayClaims/xui/0/uhs").and_then(|x| x.as_str()).context("Xbox Live gave no user hash")?;
    let (st, mc) = json_post("https://api.minecraftservices.com/authentication/login_with_xbox", &json!({"identityToken": format!("XBL3.0 x={uhs};{xsts_token}")}))?;
    let mc_token = mc.get("access_token").and_then(|x| x.as_str()).with_context(|| format!("Minecraft sign-in failed ({st})"))?.to_string();
    let expires = mc.get("expires_in").and_then(|x| x.as_u64()).unwrap_or(3600);
    let (st, prof) = bearer_get("https://api.minecraftservices.com/minecraft/profile", &mc_token)?;
    if st == 404 {
        bail!("This account doesn't own Minecraft: Java Edition.");
    }
    let uuid = prof.get("id").and_then(|x| x.as_str()).with_context(|| format!("couldn't read the Minecraft profile ({st})"))?.to_string();
    let name = prof.get("name").and_then(|x| x.as_str()).unwrap_or("Player").to_string();
    Ok(Account { uuid, name, refresh_token, mc_token, mc_expires: util::now() + expires })
}

/// The signed-in player for a launch (signs in again quietly if the Minecraft token ran out).
pub fn active_auth() -> Result<Auth> {
    let mut s = load();
    let id = s.active.clone().context("Sign in with a Microsoft account that owns Minecraft first.")?;
    let acc = s.accounts.iter().find(|a| a.uuid == id).cloned().context("Sign in with a Microsoft account that owns Minecraft first.")?;
    let acc = if acc.mc_expires > util::now() + 300 {
        acc
    } else {
        if !available() || acc.refresh_token.is_empty() {
            bail!("Your sign-in ran out. Sign in again.");
        }
        let v = form(&format!("{MS}/token"), &[("grant_type", "refresh_token"), ("client_id", CLIENT_ID), ("refresh_token", &acc.refresh_token), ("scope", SCOPE)])?;
        let ms = v.get("access_token").and_then(|x| x.as_str()).context("Your sign-in ran out. Sign in again.")?;
        let refresh = v.get("refresh_token").and_then(|x| x.as_str()).map(String::from).unwrap_or(acc.refresh_token.clone());
        let fresh = finish(ms, refresh)?;
        s.accounts.retain(|a| a.uuid != fresh.uuid);
        s.accounts.push(fresh.clone());
        save(&s)?;
        fresh
    };
    Ok(Auth { name: acc.name, uuid: acc.uuid, access_token: acc.mc_token, xuid: String::new(), client_id: CLIENT_ID.to_string() })
}
