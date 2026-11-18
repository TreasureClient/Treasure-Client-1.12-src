//! Treasure Client: Tier Tagger. Shows a player's Eaglercraft Ranked tier and role
//! (play.eaglercraftranked.com) and their EaglerTiers tier (eaglertiers.com) before their
//! name, e.g. "[Mod] G3 HT3 | Steve". The same as the JS and WASM editions.
//!
//! The lookups are made by the Treasure Settings overlay: names to look up go in the
//! `tcttreq` storage key, and the overlay writes the results to `tctiers` as lines of
//! `name=role|overall:gold_3,sword:emerald_1,...|sword:HT3,...` (empty = not found).

use std::collections::{HashMap, HashSet};
use std::sync::Mutex;

use crate::text::Span;

#[derive(Default)]
struct State {
    data: HashMap<String, String>,
    requested: HashSet<String>,
    pending: Vec<String>,
    last_poll: Option<crate::platform::time::Instant>,
    last_raw: String,
    shown: HashMap<(String, String), Vec<Span>>,
}

static STATE: Mutex<Option<State>> = Mutex::new(None);

fn valid_name(n: &str) -> bool {
    (3..=16).contains(&n.len()) && n.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
}

#[cfg(target_arch = "wasm32")]
fn storage() -> Option<web_sys::Storage> {
    web_sys::window()?.local_storage().ok()?
}

/// Every half second: send new lookups to the overlay and read its results.
fn poll(st: &mut State) {
    let now = crate::platform::time::Instant::now();
    if st.last_poll.is_some_and(|t| now.duration_since(t).as_millis() < 500) {
        return;
    }
    st.last_poll = Some(now);
    #[cfg(target_arch = "wasm32")]
    {
        use base64::Engine as _;
        let b64 = base64::engine::general_purpose::STANDARD;
        let Some(s) = storage() else { return };
        const REQ: &str = "_eaglercraft_1.12.tcttreq";
        if !st.pending.is_empty() {
            let mut list = s
                .get_item(REQ)
                .ok()
                .flatten()
                .and_then(|raw| b64.decode(raw.trim()).ok())
                .and_then(|b| String::from_utf8(b).ok())
                .unwrap_or_default();
            for name in st.pending.drain(..) {
                if !list.is_empty() {
                    list.push(',');
                }
                list.push_str(&name);
            }
            let _ = s.set_item(REQ, &b64.encode(list.as_bytes()));
        }
        let raw = s
            .get_item("_eaglercraft_1.12.tctiers")
            .ok()
            .flatten()
            .and_then(|raw| b64.decode(raw.trim()).ok())
            .and_then(|b| String::from_utf8(b).ok())
            .unwrap_or_default();
        if raw != st.last_raw {
            for line in raw.lines() {
                if let Some((name, data)) = line.split_once('=') {
                    st.data.insert(name.to_lowercase(), data.to_owned());
                }
            }
            st.last_raw = raw;
        }
    }
    #[cfg(not(target_arch = "wasm32"))]
    st.pending.clear();
}

/// The spans to put before a player's name (tag and a grey bar), or `None`.
pub fn prefix(name: &str) -> Option<Vec<Span>> {
    let mods = crate::tc_mods::get();
    if !mods.tier_tagger {
        return None;
    }
    let mut guard = STATE.lock().unwrap();
    let st = guard.get_or_insert_with(State::default);
    poll(st);
    let key = name.to_lowercase();
    let Some(data) = st.data.get(&key).cloned() else {
        if valid_name(name) && st.requested.insert(key) {
            st.pending.push(name.to_owned());
        }
        return None;
    };
    let cache_key = (mods.tt_mode.clone(), data.clone());
    if let Some(spans) = st.shown.get(&cache_key) {
        return (!spans.is_empty()).then(|| spans.clone());
    }
    let mut spans = format(&data, &mods.tt_mode);
    if !spans.is_empty() {
        spans.push(Span::colored(" | ", 0x55_5555));
    }
    if st.shown.len() > 512 {
        st.shown.clear();
    }
    st.shown.insert(cache_key, spans.clone());
    (!spans.is_empty()).then_some(spans)
}

/// The coloured tag ("[Admin] G3 HT3") for the chosen gamemode ("highest" = overall rank).
fn format(data: &str, mode: &str) -> Vec<Span> {
    let parts: Vec<&str> = data.split('|').collect();
    if parts.len() < 2 {
        return Vec::new();
    }
    let (mut overall, mut chosen) = (None, None);
    for entry in parts[1].split(',') {
        let Some((m, rank)) = entry.split_once(':') else { continue };
        if m == "overall" {
            overall = Some(rank);
        } else if m == mode {
            chosen = Some(rank);
        }
    }
    let tags = [
        role_tag(parts[0]),
        tier_tag(chosen).or_else(|| tier_tag(overall)),
        parts.get(2).and_then(|et| eagler_tiers_tag(et, mode)),
    ];
    let mut out = Vec::new();
    for tag in tags.into_iter().flatten() {
        if !out.is_empty() {
            out.push(Span::colored(" ", 0xFF_FFFF));
        }
        out.extend(tag);
    }
    out
}

/// EaglerTiers tag for the gamemode, or their best tier. Data is `sword:HT3,axe:RLT2,...`
/// (R = retired).
fn eagler_tiers_tag(data: &str, mode: &str) -> Option<Vec<Span>> {
    if data.is_empty() {
        return None;
    }
    let mode = if mode == "netheriteop" { "nethop" } else { mode };
    let (mut best, mut best_score, mut chosen) = (None, i32::MAX, None);
    for entry in data.split(',') {
        let Some((m, t)) = entry.split_once(':') else { continue };
        let retired = t.starts_with('R');
        let code = t.trim_start_matches('R');
        let b = code.as_bytes();
        if b.len() != 3 || b[1] != b'T' || !(b'1'..=b'5').contains(&b[2]) {
            continue;
        }
        // lower is better: HT1 < LT1 < HT2 ..., active beats retired
        let score = i32::from(b[2] - b'0') * 2 + i32::from(b[0] != b'H') + if retired { 100 } else { 0 };
        if score < best_score {
            best_score = score;
            best = Some(t);
        }
        if m == mode {
            chosen = Some(t);
        }
    }
    let t = chosen.or(best)?;
    let retired = t.starts_with('R');
    let code = t.trim_start_matches('R');
    let high = code.starts_with('H');
    let color = match code.as_bytes()[2] {
        b'1' => if high { 0xFF_AA00 } else { 0xFF_FF55 },
        b'2' => if high { 0xFF_FFFF } else { 0xAA_AAAA },
        b'3' => 0xFF_5555,
        _ => if high { 0x55_55FF } else { 0x55_5555 },
    };
    let mut out = Vec::new();
    if retired {
        out.push(Span::colored("R", 0x55_5555));
    }
    out.push(Span::colored(code, color));
    Some(out)
}

/// "gold_3" -> "G3", "netherite" -> "NT", in the Eaglercraft Ranked site colours.
fn tier_tag(rank: Option<&str>) -> Option<Vec<Span>> {
    let (color, code) = match rank? {
        "coal_1" => (0x4A_4A4A, "C1"),
        "coal_2" => (0x5C_5C5C, "C2"),
        "coal_3" => (0x70_7070, "C3"),
        "iron_1" => (0x9F_A6AD, "I1"),
        "iron_2" => (0xB7_BEC4, "I2"),
        "iron_3" => (0xD4_D9DD, "I3"),
        "gold_1" => (0xD9_A824, "G1"),
        "gold_2" => (0xE8_BC2F, "G2"),
        "gold_3" => (0xF7_D33E, "G3"),
        "emerald_1" => (0x2F_9E57, "E1"),
        "emerald_2" => (0x25_B869, "E2"),
        "emerald_3" => (0x1F_D67D, "E3"),
        "diamond_1" => (0x3F_C7E0, "D1"),
        "diamond_2" => (0x4F_DCF5, "D2"),
        "diamond_3" => (0x7B_E9FF, "D3"),
        "netherite" => (0xB9_6CFF, "NT"),
        "champion" => (0xFF_3B3B, "CH"),
        _ => return None,
    };
    Some(vec![Span::colored(code, color)])
}

/// Eaglercraft Ranked role tag in the site's role colour.
fn role_tag(role: &str) -> Option<Vec<Span>> {
    let (color, label) = match role {
        "admin" => (0xBB_0C00, "Admin"),
        "moderator" => (0x2E_CC71, "Mod"),
        "helper" => (0xAD_D8E6, "Helper"),
        "media" => (0xFF_4A2A, "Media"),
        "booster" => (0xFF_73FA, "Booster"),
        "supporter" => (0xF4_B7FF, "Supporter"),
        "premium" => (0xFF_D700, "Premium"),
        _ => return None,
    };
    Some(vec![Span::colored(format!("[{label}]"), color)])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn builds_the_tag() {
        let text: String = format("moderator|overall:gold_3,sword:emerald_1|sword:HT3", "sword")
            .iter()
            .map(|s| s.text.as_str())
            .collect();
        assert_eq!(text, "[Mod] E1 HT3");
    }
}
