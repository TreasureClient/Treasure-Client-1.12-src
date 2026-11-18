//! Treasure Client: the chat mods, the same as the JS and WASM editions.
//!
//! - AutoGG: says your message (default "gg") when a game ends, and optionally "glhf"
//!   when one starts. Once per game, after the delay set in the Mod Menu.
//! - Auto Login: remembers the password you type in /login or /register, separately for
//!   each server and name, and types /login for you when that server asks (once per join).
//!   Forgets a password the server says is wrong. `.autologin`, `.autologin forget`,
//!   `.autologin forgetall`.

use std::collections::BTreeMap;
use std::sync::Mutex;

use base64::Engine as _;

use crate::session::SessionState;

const STORE_KEY: &str = "_eaglercraft_1.12.tclogin";

const END: &[&str] = &[
    "winner:", "winners:", "winning team:", "winner is", "1st killer", "#1 killer", "1st place",
    "won the game", "won the match", "won the duel", "game over", "match over", "match ended",
    "game ended", "duel ended", "reward summary", "victory!", "defeat!",
];
const END_TITLE: &[&str] = &[
    "victory", "victory!", "defeat", "defeat!", "game over", "game over!", "you won", "you won!",
    "you win", "you win!", "you lost", "you lost!", "draw", "draw!",
];
const START: &[&str] = &[
    "the game starts in 1 second", "the game has started", "the match has started",
    "the game has begun", "the match has begun", "game started!", "match started!", "fight!",
];
const GG_COOLDOWN_MS: f64 = 10_000.0;
const LOGIN_DELAY_MS: f64 = 900.0;
const ANSWER_MS: f64 = 10_000.0;

#[derive(Default)]
struct State {
    last_gg: f64,
    last_glhf: f64,
    /// (message, send at)
    pending: Vec<(String, f64)>,
    /// Lines to show in chat (from Auto Login).
    notes: Vec<String>,
    saved: Option<BTreeMap<String, String>>,
    prompted_for: Option<String>,
    sent: Option<(String, f64)>,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn now_ms() -> f64 {
    static START: std::sync::OnceLock<crate::platform::time::Instant> = std::sync::OnceLock::new();
    START
        .get_or_init(crate::platform::time::Instant::now)
        .elapsed()
        .as_secs_f64()
        * 1000.0
}

fn with<R>(f: impl FnOnce(&mut State) -> R) -> R {
    let mut guard = STATE.lock().unwrap();
    f(guard.get_or_insert_with(State::default))
}

/// Someone typed it in chat (a name and a colon or an arrow before the phrase).
fn from_server(t: &str, phrase: &str) -> bool {
    let Some(i) = t.find(phrase) else { return false };
    let before = &t[..i];
    !before.contains('>') && !before.contains('\u{00BB}') && !before.contains(':')
}

/// The server and name a password belongs to ("address|name").
fn key(session: &SessionState) -> Option<String> {
    let address = session.current_address.as_ref()?.trim().to_lowercase();
    let name = crate::gui::accountlist::active_username()?.to_lowercase();
    (!address.is_empty() && !name.is_empty()).then(|| format!("{address}|{name}"))
}

fn saved(st: &mut State) -> &mut BTreeMap<String, String> {
    st.saved.get_or_insert_with(load)
}

#[cfg(target_arch = "wasm32")]
fn storage() -> Option<web_sys::Storage> {
    web_sys::window()?.local_storage().ok()?
}

/// Same format as the JS and WASM editions: base64 of lines `key\tbase64(password)`.
fn load() -> BTreeMap<String, String> {
    let mut map = BTreeMap::new();
    #[cfg(target_arch = "wasm32")]
    {
        let b64 = base64::engine::general_purpose::STANDARD;
        let text = storage()
            .and_then(|s| s.get_item(STORE_KEY).ok().flatten())
            .and_then(|raw| b64.decode(raw.trim()).ok())
            .and_then(|bytes| String::from_utf8(bytes).ok())
            .unwrap_or_default();
        for line in text.lines() {
            if let Some((k, pw)) = line.split_once('\t')
                && let Some(pw) = b64.decode(pw).ok().and_then(|b| String::from_utf8(b).ok())
            {
                map.insert(k.to_owned(), pw);
            }
        }
    }
    map
}

fn store(map: &BTreeMap<String, String>) {
    #[cfg(target_arch = "wasm32")]
    {
        let b64 = base64::engine::general_purpose::STANDARD;
        let text: String = map
            .iter()
            .map(|(k, pw)| format!("{k}\t{}\n", b64.encode(pw.as_bytes())))
            .collect();
        if let Some(s) = storage() {
            let _ = if text.is_empty() {
                s.remove_item(STORE_KEY)
            } else {
                s.set_item(STORE_KEY, &b64.encode(text.as_bytes()))
            };
        }
    }
    #[cfg(not(target_arch = "wasm32"))]
    let _ = map;
}

/// A chat line from the server.
pub fn on_chat(session: &SessionState, text: &str) {
    let mods = crate::tc_mods::get();
    let t = text.trim().to_lowercase();
    if t.is_empty() {
        return;
    }
    with(|st| {
        let now = now_ms();
        if mods.auto_gg && t.len() <= 120 {
            if END.iter().any(|p| from_server(&t, p)) {
                game_ended(st, &mods, now);
            } else if mods.gg_glhf
                && START.iter().any(|p| from_server(&t, p))
                && now - st.last_glhf > GG_COOLDOWN_MS
                && now - st.last_gg > GG_COOLDOWN_MS
            {
                st.last_glhf = now;
                st.pending.push(("glhf".to_owned(), now + 300.0));
            }
        }
        if !mods.auto_login || t.len() > 200 {
            return;
        }
        if let Some((sent_key, at)) = st.sent.clone()
            && now - at < ANSWER_MS
            && from_server(&t, "password")
            && ["wrong", "incorrect", "invalid", "not correct"].iter().any(|w| t.contains(w))
        {
            saved(st).remove(&sent_key);
            store(saved(st));
            st.sent = None;
            st.notes.push(
                "\u{00A7}cThe saved password was wrong, so it was forgotten. \u{00A7}7Type /login yourself to save the new one."
                    .to_owned(),
            );
            return;
        }
        if !from_server(&t, "/login") && !from_server(&t, "/l <") && !from_server(&t, "/l [") {
            return;
        }
        let Some(k) = key(session) else { return };
        if st.prompted_for.as_deref() == Some(k.as_str()) {
            return; // once per join: never spam a server with attempts
        }
        let Some(pw) = saved(st).get(&k).cloned() else { return };
        st.prompted_for = Some(k.clone());
        st.sent = Some((k, now + LOGIN_DELAY_MS));
        st.pending.push((format!("/login {pw}"), now + LOGIN_DELAY_MS));
        st.notes.push("\u{00A7}7Logged you in with your saved password.".to_owned());
    });
}

/// A title on the screen (big text in the middle).
pub fn on_title(text: &str) {
    let mods = crate::tc_mods::get();
    if !mods.auto_gg {
        return;
    }
    let t = text.trim().to_lowercase();
    if END_TITLE.contains(&t.as_str()) {
        with(|st| game_ended(st, &mods, now_ms()));
    }
}

fn game_ended(st: &mut State, mods: &crate::tc_mods::TcMods, now: f64) {
    if now - st.last_gg < GG_COOLDOWN_MS {
        return; // one gg per game, even when the server sends several end messages
    }
    st.last_gg = now;
    st.pending.push((mods.gg_message.clone(), now + f64::from(mods.gg_delay_ms)));
}

/// A line you typed. Returns false when it was a Treasure Client command (not sent).
pub fn on_typed(session: &mut SessionState, line: &str) -> bool {
    let trimmed = line.trim();
    let lower = trimmed.to_lowercase();
    if lower == ".autologin" || lower.starts_with(".autologin ") {
        command(session, lower.trim_start_matches(".autologin").trim());
        return false;
    }
    let mods = crate::tc_mods::get();
    if !mods.auto_login || !trimmed.starts_with('/') {
        return true;
    }
    let parts: Vec<&str> = trimmed.split_whitespace().collect();
    if parts.len() < 2 {
        return true;
    }
    let cmd = parts[0].to_lowercase();
    let pw = match cmd.as_str() {
        "/login" | "/l" | "/log" => Some(parts[1]),
        "/register" | "/reg" if parts.len() == 2 || parts[1] == parts[2] => Some(parts[1]),
        "/changepassword" | "/changepass" if parts.len() >= 3 => Some(parts[2]),
        _ => None,
    };
    let (Some(pw), Some(k)) = (pw, key(session)) else { return true };
    if pw.len() > 100 {
        return true;
    }
    with(|st| {
        if saved(st).get(&k).map(String::as_str) != Some(pw) {
            saved(st).insert(k, pw.to_owned());
            store(saved(st));
            st.notes.push("\u{00A7}7Password saved for this server. It will log you in next time.".to_owned());
        }
    });
    true
}

fn command(session: &SessionState, args: &str) {
    let k = key(session);
    let on = crate::tc_mods::get().auto_login;
    with(|st| {
        let note = match args {
            "forgetall" => {
                let n = saved(st).len();
                saved(st).clear();
                store(saved(st));
                format!("\u{00A7}7Forgot {n} saved password{}.", if n == 1 { "" } else { "s" })
            }
            "forget" => match k.as_ref().and_then(|k| saved(st).remove(k)) {
                Some(_) => {
                    store(saved(st));
                    "\u{00A7}7Forgot the saved password for this server.".to_owned()
                }
                None => "\u{00A7}7No saved password for this server.".to_owned(),
            },
            _ => {
                let here = k.as_ref().is_some_and(|k| saved(st).contains_key(k));
                let n = saved(st).len();
                format!(
                    "\u{00A7}7{}. {} Saved for {n} server{}. \u{00A7}8.autologin forget \u{00B7} .autologin forgetall",
                    if on { "On" } else { "Off (turn it on in the mod menu)" },
                    if k.is_none() {
                        "Not on a server."
                    } else if here {
                        "This server has a saved password."
                    } else {
                        "No saved password for this server."
                    },
                    if n == 1 { "" } else { "s" },
                )
            }
        };
        st.notes.push(note);
    });
}

/// Every client tick: send what is due and show notes in chat.
pub fn tick(session: &mut SessionState) {
    let (due, notes) = with(|st| {
        let now = now_ms();
        let mut due = Vec::new();
        st.pending.retain(|(msg, at)| {
            if *at <= now {
                due.push(msg.clone());
                false
            } else {
                true
            }
        });
        if let Some((_, at)) = st.sent.as_mut()
            && due.iter().any(|m| m.starts_with("/login "))
        {
            *at = now; // the server's answer counts from when it was really sent
        }
        (due, std::mem::take(&mut st.notes))
    });
    if session.current_address.is_none() {
        with(|st| st.prompted_for = None);
    }
    for msg in due {
        if !msg.is_empty() {
            session.outgoing_chat.push(msg);
        }
    }
    for note in notes {
        let mut spans = crate::text::parse_formatted("\u{00A7}6[Treasure Client] ");
        spans.extend(crate::text::parse_formatted(&note));
        session.chat_incoming.push(crate::session::ChatEntry {
            spans,
            events: None,
            tag: crate::session::ChatTag::System,
            original: None,
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn player_chat_does_not_count() {
        assert!(from_server("winner: steve", "winner:"));
        assert!(!from_server("<alex> winner: steve", "winner:"));
        assert!(!from_server("alex: please /login", "/login"));
    }
}
