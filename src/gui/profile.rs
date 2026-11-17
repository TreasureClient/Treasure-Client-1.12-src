use crate::gui::accountlist::{self, Account, AccountKind, Accounts};
use crate::gui::focus;
use crate::gui::multiplayer::{
    self, BUTTON_GAP, CONTENT_PAD, FOOTER_H, ICON_SIZE, LAYOUT, LOWER_ROW_BUTTON_W, ROW_H, ROW_W,
    TEXT_INSET, row_left,
};
use crate::gui::painter::Painter;
use crate::gui::widgets::{self, Button, TextBox, WIDGET_HEIGHT, WIDGET_WIDTH_BIG};
use crate::gui::{GuiState, Screen, ScreenCtx, menu};
use crate::platform::cli::valid_username;
use crate::text::{AQUA, GREEN};

const TITLE: &str = "Accounts";
const SELECT: &str = "Select Account";
const ADD: &str = "Add Account";
const ADD_TITLE: &str = "Add Account";
const EDIT_TITLE: &str = "Edit Account";
const USERNAME_LABEL: &str = "Username";
const EDIT: &str = "Edit";
const DELETE: &str = "Delete";
const BACK: &str = "Back";
const DONE: &str = "Done";
const CANCEL: &str = "Cancel";
const IN_USE: &str = "Selected";
const EMPTY: &str = "No accounts yet. Add one to choose a name to join under.";
const DELETE_QUESTION: &str = "Are you sure you want to remove this account?";

const USERNAME_MAX_LEN: usize = 16;

const BAR_W: f32 = 3.0 * LOWER_ROW_BUTTON_W + 2.0 * BUTTON_GAP;
const TOP_ROW_BUTTON_W: f32 = (BAR_W - BUTTON_GAP) / 2.0;
const GREY: u32 = 0x808080;

pub struct ProfileState {
    pub accounts: Accounts,
    loaded: bool,
    pub selected: Option<usize>,
    pub scroll: f32,
    scrollbar_drag: Option<f32>,
    editing: Option<usize>,
    username: TextBox,
    #[cfg(eagler_skins)]
    skin: crate::gui::skinpick::SkinPick,
    confirm_delete: Option<usize>,
    pub first_run: bool,
    pub pending_connect: Option<String>,
    #[cfg(feature = "online_mode")]
    copied_link: bool,
    /// Treasure Client: the Treasure Client profile screens.
    pub(crate) tc: TcUi,
}

impl Default for ProfileState {
    fn default() -> ProfileState {
        ProfileState {
            accounts: Accounts::default(),
            loaded: false,
            selected: None,
            scroll: 0.0,
            scrollbar_drag: None,
            editing: None,
            username: TextBox::bordered(USERNAME_MAX_LEN, ""),
            #[cfg(eagler_skins)]
            skin: Default::default(),
            confirm_delete: None,
            first_run: false,
            pending_connect: None,
            #[cfg(feature = "online_mode")]
            copied_link: false,
            tc: TcUi::default(),
        }
    }
}

impl ProfileState {
    pub fn starting(first_run: bool, pending_connect: Option<String>) -> ProfileState {
        let mut state = ProfileState {
            first_run,
            pending_connect,
            ..Default::default()
        };
        if first_run && entry_screen() == Screen::EditProfile {
            state.username.focus();
        }
        state
    }

    pub fn ensure_loaded(&mut self) {
        if self.loaded {
            return;
        }
        self.accounts = accountlist::load();
        self.loaded = true;
    }

    pub fn open(&mut self) {
        self.ensure_loaded();
        self.selected = self.accounts.active;
        self.confirm_delete = None;
    }

    pub fn save(&self) {
        accountlist::save(&self.accounts);
    }

    fn begin_add(&mut self) {
        self.editing = None;
        self.username.set_text("");
        self.username.focus();
        #[cfg(eagler_skins)]
        self.skin.open(None);
    }

    fn begin_edit(&mut self, index: usize) {
        let Some(account) = self.accounts.get(index).filter(|a| a.kind.editable()) else {
            return;
        };
        let name = account.name.clone();
        self.editing = Some(index);
        self.username.set_text(&name);
        self.username.focus();
        #[cfg(eagler_skins)]
        self.skin.open(Some(&name));
    }

    fn commit_form(&mut self) {
        let name = self.username.text.trim().to_string();
        if !valid_username(&name) {
            return;
        }
        #[cfg(eagler_skins)]
        {
            let old = self
                .editing
                .and_then(|i| self.accounts.get(i))
                .map(|a| a.name.clone());
            self.skin.commit(old.as_deref(), &name);
        }
        match self.editing {
            Some(i) if i < self.accounts.entries.len() => {
                self.accounts.entries[i].name = name;
                self.selected = Some(i);
            }
            _ => {
                self.accounts.add(Account {
                    name,
                    kind: AccountKind::Offline,
                    ..Account::default()
                });
                self.selected = self.accounts.active;
            }
        }
        self.save();
        self.publish();
    }

    fn publish(&self) {
        if let Some(account) = self.accounts.active() {
            crate::client::bot::set_username(account.name.clone());
        }
        #[cfg(feature = "online_mode")]
        crate::client::ban::account_changed();
    }
}

fn leave(state: &mut GuiState, back: Screen) {
    if !state.profile.first_run {
        state.nav = Some(back);
        return;
    }
    state.profile.first_run = false;
    match state.profile.pending_connect.take() {
        Some(address) => {
            state.connect = Some(address);
            state.nav = Some(Screen::None);
        }
        None => state.nav = Some(Screen::Title),
    }
}

pub fn entry_screen() -> Screen {
    #[cfg(feature = "online_mode")]
    {
        Screen::AddAccount
    }
    #[cfg(not(feature = "online_mode"))]
    {
        Screen::EditProfile
    }
}

#[allow(dead_code)]
fn torch_draw(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    state.profile.ensure_loaded();
    menu::background(p, ctx.vw, ctx.vh);
    p.text_centered(TITLE, ctx.vw / 2.0, LAYOUT.title_y(), 0xFFFFFF, true);

    let (lx, ly, lw, lh) = LAYOUT.content_rect(ctx.vw, ctx.vh);
    menu::list_background(p, lx, ly, lw, lh, state.profile.scroll);

    let confirming = state.profile.confirm_delete.is_some();
    draw_rows(p, state, ctx, confirming);
    draw_scrollbar(p, state, ctx, lh, confirming);
    draw_footer(p, state, ctx, confirming);

    if confirming {
        draw_delete_confirm(p, state, ctx);
    } else {
        handle_list_keys(state, ctx);
    }
}

fn draw_rows(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx, locked: bool) {
    let (lx, ly, lw, lh) = LAYOUT.content_rect(ctx.vw, ctx.vh);
    let st = &mut state.profile;
    multiplayer::clamp_list_scroll(&mut st.scroll, st.accounts.entries.len(), lh);

    let list_focused = focus::next(!locked && !st.accounts.entries.is_empty());

    if st.accounts.entries.is_empty() {
        p.text_centered(
            EMPTY,
            ctx.vw / 2.0,
            (ly + lh / 2.0 - 4.0).floor(),
            GREY,
            true,
        );
        return;
    }

    let mut activate: Option<usize> = None;
    let over_list = !locked && ctx.hovering(lx, ly, lw, lh);

    let guard = p.push_clip(lx, ly, lw, lh);
    for i in 0..st.accounts.entries.len() {
        let rx = row_left(ctx.vw);
        let ry = ly + CONTENT_PAD - st.scroll + i as f32 * ROW_H;
        if ry + ROW_H < ly || ry > ly + lh {
            continue;
        }
        if st.selected == Some(i) {
            multiplayer::draw_selection_outline(p, rx, ry, list_focused);
        }

        let (cx, cy) = (rx + CONTENT_PAD, ry + CONTENT_PAD);
        let cw = ROW_W - 2.0 * CONTENT_PAD;
        draw_row_content(
            p,
            &st.accounts.entries[i],
            st.accounts.active == Some(i),
            cx,
            cy,
            cw,
        );

        if over_list && ctx.input.left_click && ctx.hovering(rx, ry, ROW_W, ROW_H) {
            st.selected = Some(i);
            focus::claim();
            if ctx.input.double_click {
                activate = Some(i);
            }
        }
    }
    p.pop_clip(guard);

    if let Some(i) = activate {
        select(state, i);
    }
}

fn draw_row_content(p: &mut Painter, account: &Account, active: bool, cx: f32, cy: f32, cw: f32) {
    p.sprite("pack_icon", cx, cy, ICON_SIZE, ICON_SIZE);

    let text_x = cx + TEXT_INSET;
    p.text_plain(
        &account.name,
        text_x,
        cy + 6.0,
        if active { GREEN } else { 0xFFFFFF },
        true,
    );
    let color = match account.kind {
        AccountKind::Microsoft => AQUA,
        AccountKind::Offline => GREY,
    };
    p.text_plain(account.kind.label(), text_x, cy + 18.0, color, true);

    if active {
        let w = p.atlas.font.width_str(IN_USE);
        p.text_plain(IN_USE, cx + cw - w, cy + 6.0, GREEN, true);
    }
}

fn draw_scrollbar(
    p: &mut Painter,
    state: &mut GuiState,
    ctx: &ScreenCtx,
    list_h: f32,
    locked: bool,
) {
    let st = &mut state.profile;
    let content_h = multiplayer::list_content_height(st.accounts.entries.len());
    multiplayer::list_scrollbar(
        p,
        ctx,
        &mut st.scroll,
        &mut st.scrollbar_drag,
        content_h,
        list_h,
        locked,
    );
}

fn draw_footer(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx, locked: bool) {
    let top = ctx.vh - FOOTER_H + ((FOOTER_H - 2.0 * WIDGET_HEIGHT - BUTTON_GAP) / 2.0).floor();
    let bottom = top + WIDGET_HEIGHT + BUTTON_GAP;

    let bar_x = widgets::centered_row_x(ctx.vw, BAR_W);
    #[cfg(feature = "tutorial")]
    crate::gui::tutorial::mark(
        crate::gui::tutorial::Spot::Accounts,
        crate::gui::hud_layout::Rect::new(bar_x, top, BAR_W, bottom + WIDGET_HEIGHT - top),
    );

    let selected = state
        .profile
        .selected
        .filter(|i| *i < state.profile.accounts.entries.len());
    let can_select = selected.is_some_and(|i| state.profile.accounts.active != Some(i));
    let can_edit = selected
        .and_then(|i| state.profile.accounts.get(i))
        .is_some_and(|a| a.kind.editable());

    let button = |p: &mut Painter, x: f32, y: f32, w: f32, label: &str, active: bool| {
        Button {
            x,
            y,
            w,
            h: WIDGET_HEIGHT,
            label,
            active: active && !locked,
        }
        .draw(p, ctx)
    };

    let select_clicked = button(p, bar_x, top, TOP_ROW_BUTTON_W, SELECT, can_select);
    let add = button(
        p,
        bar_x + TOP_ROW_BUTTON_W + BUTTON_GAP,
        top,
        TOP_ROW_BUTTON_W,
        ADD,
        true,
    );

    let step = LOWER_ROW_BUTTON_W + BUTTON_GAP;
    let edit = button(p, bar_x, bottom, LOWER_ROW_BUTTON_W, EDIT, can_edit);
    let delete = button(
        p,
        bar_x + step,
        bottom,
        LOWER_ROW_BUTTON_W,
        DELETE,
        selected.is_some(),
    );
    let back = button(
        p,
        bar_x + 2.0 * step,
        bottom,
        LOWER_ROW_BUTTON_W,
        BACK,
        true,
    );

    if select_clicked && let Some(i) = selected {
        select(state, i);
    }
    if add {
        state.profile.begin_add();
        state.nav = Some(entry_screen());
    }
    if edit && let Some(i) = selected {
        state.profile.begin_edit(i);
        state.nav = Some(Screen::EditProfile);
    }
    if delete && let Some(i) = selected {
        state.profile.confirm_delete = Some(i);
    }
    if back {
        state.nav = Some(Screen::Title);
    }
}

fn select(state: &mut GuiState, index: usize) {
    state.profile.accounts.activate(index);
    state.profile.selected = Some(index);
    state.profile.save();
    state.profile.publish();
}

fn handle_list_keys(state: &mut GuiState, ctx: &ScreenCtx) {
    let input = ctx.input;
    if input.escape {
        state.nav = Some(Screen::Title);
        return;
    }
    let count = state.profile.accounts.entries.len();
    multiplayer::arrow_select(
        &mut state.profile.selected,
        count,
        input.up_arrow,
        input.down_arrow,
    );
    if input.enter
        && let Some(i) = state.profile.selected.filter(|i| *i < count)
    {
        select(state, i);
    }
}

fn draw_delete_confirm(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    let Some(index) = state.profile.confirm_delete else {
        return;
    };
    let name = state
        .profile
        .accounts
        .get(index)
        .map(|a| a.name.clone())
        .unwrap_or_default();

    p.fill(0.0, 0.0, ctx.vw, ctx.vh, 0xC0_000000);

    let cy = (ctx.vh / 2.0).floor();
    p.text_centered(DELETE_QUESTION, ctx.vw / 2.0, cy - 40.0, 0xFFFFFF, true);
    let warning = format!("'{name}' will be removed from this client.");
    p.text_centered(&warning, ctx.vw / 2.0, cy - 25.0, GREY, true);

    let x = widgets::centered_x(ctx.vw);
    if Button::new(x, cy, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, DELETE).draw(p, ctx) {
        state.profile.accounts.remove(index);
        if state.profile.accounts.active.is_none() && !state.profile.accounts.entries.is_empty() {
            state.profile.accounts.activate(0);
        }
        state.profile.selected = state.profile.accounts.active;
        state.profile.confirm_delete = None;
        state.profile.save();
        state.profile.publish();
        if state.profile.accounts.entries.is_empty() {
            state.profile.first_run = true;
            state.profile.begin_add();
            state.nav = Some(entry_screen());
        }
    }
    if Button::new(x, cy + 24.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, CANCEL).draw(p, ctx)
        || ctx.input.escape
    {
        state.profile.confirm_delete = None;
    }
}

#[cfg(feature = "online_mode")]
pub fn draw_add(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    const QUESTION: &str = "Do you want to add a Microsoft account or play offline?";
    const REASSURANCE: &str = "(This option can be changed later)";
    const MICROSOFT: &str = "Microsoft Account";
    const OFFLINE: &str = "Play Offline";

    state.profile.ensure_loaded();
    menu::background(p, ctx.vw, ctx.vh);
    p.text_centered(ADD_TITLE, ctx.vw / 2.0, LAYOUT.title_y(), 0xFFFFFF, true);

    let first_run = state.profile.first_run;
    let base = (ctx.vh / 4.0).floor();
    p.text_centered(QUESTION, ctx.vw / 2.0, base + 20.0, 0xFFFFFF, true);
    if first_run {
        p.text_centered(REASSURANCE, ctx.vw / 2.0, base + 34.0, GREY, true);
    }

    let x = widgets::centered_x(ctx.vw);
    let microsoft = Button::new(x, base + 60.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, MICROSOFT);
    let offline = Button::new(x, base + 84.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, OFFLINE);

    if microsoft.draw(p, ctx) {
        state.profile.copied_link = false;
        crate::client::auth::begin();
        state.nav = Some(Screen::MicrosoftLogin);
    }
    if offline.draw(p, ctx) {
        state.profile.begin_add();
        state.nav = Some(Screen::EditProfile);
    }

    if !first_run {
        let cancel = Button::new(x, base + 114.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, CANCEL);
        if cancel.draw(p, ctx) || ctx.input.escape {
            state.nav = Some(Screen::Accounts);
        }
    }
}

#[cfg(feature = "online_mode")]
pub fn draw_microsoft(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    use crate::client::auth::{self, Auth};

    const MS_TITLE: &str = "Sign in with Microsoft";
    const STARTING: &str = "Contacting Microsoft...";
    const INSTRUCTION: &str = "Open the link below and enter this code:";
    const ELSEWHERE: &str = "You can do this on another device, such as a phone.";
    const OPEN: &str = "Open Link";
    const COPY: &str = "Copy Link";
    const COPIED: &str = "Copied";
    const RETRY: &str = "Try Again";
    use crate::text::{RED, YELLOW};

    state.profile.ensure_loaded();
    menu::background(p, ctx.vw, ctx.vh);
    p.text_centered(MS_TITLE, ctx.vw / 2.0, LAYOUT.title_y(), 0xFFFFFF, true);

    let x = widgets::centered_x(ctx.vw);
    let base = (ctx.vh / 4.0).floor();
    let centered = |p: &mut Painter, text: &str, y: f32, color: u32| {
        p.text_centered(text, ctx.vw / 2.0, y, color, true);
    };

    match auth::state() {
        None | Some(Auth::Starting) => {
            centered(p, STARTING, base + 30.0, 0xFFFFFF);
        }
        Some(Auth::Prompt { url, code }) => {
            centered(p, INSTRUCTION, base + 14.0, 0xFFFFFF);
            centered(p, &code, base + 30.0, YELLOW);
            centered(p, ELSEWHERE, base + 46.0, GREY);

            if Button::new(x, base + 64.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, OPEN).draw(p, ctx) {
                crate::platform::url::open(&url);
            }
            let copied = state.profile.copied_link;
            let copy_label = if copied { COPIED } else { COPY };
            if Button::new(x, base + 88.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, copy_label).draw(p, ctx)
            {
                crate::platform::clipboard::set(&url);
                state.profile.copied_link = true;
            }
        }
        Some(Auth::Done {
            name,
            uuid,
            token,
            session,
            session_expires,
        }) => {
            state.profile.accounts.add(Account {
                name,
                kind: AccountKind::Microsoft,
                token: Some(token),
                uuid: Some(uuid),
                session: Some(session),
                session_expires: Some(session_expires),
            });
            state.profile.selected = state.profile.accounts.active;
            state.profile.save();
            state.profile.publish();
            state.profile.copied_link = false;
            auth::clear();
            leave(state, Screen::Accounts);
            return;
        }
        Some(Auth::Failed(message)) => {
            centered(p, &message, base + 30.0, RED);
            if Button::new(x, base + 64.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, RETRY).draw(p, ctx) {
                state.profile.copied_link = false;
                auth::begin();
            }
        }
    }

    let cancel = Button::new(x, base + 122.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT, CANCEL);
    if cancel.draw(p, ctx) || ctx.input.escape {
        auth::cancel();
        state.profile.copied_link = false;
        state.nav = Some(entry_screen());
    }
}

#[allow(dead_code)]
fn torch_draw_edit(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    state.profile.ensure_loaded();
    menu::background(p, ctx.vw, ctx.vh);

    let editing = state.profile.editing.is_some();
    let title = if editing { EDIT_TITLE } else { ADD_TITLE };
    let x = widgets::centered_x(ctx.vw);
    p.text_centered(title, ctx.vw / 2.0, 17.0, 0xFFFFFF, true);
    #[cfg(eagler_skins)]
    let (x, buttons, bottom) = {
        let x = x + 60.0;
        let buttons = 92.0 + crate::gui::skinpick::HEIGHT + 8.0;
        let bottom = buttons + WIDGET_HEIGHT + 6.0;
        p.fill(
            x - 124.0,
            44.0,
            WIDGET_WIDTH_BIG + 128.0,
            bottom - 44.0,
            0x80000000,
        );
        (x, buttons, bottom)
    };
    p.text_plain(USERNAME_LABEL, x + 1.0, 53.0, 0xA0A0A0, true);

    let field = &mut state.profile.username;
    field.update(p, ctx, x, 66.0, WIDGET_WIDTH_BIG, WIDGET_HEIGHT);

    let valid = valid_username(&field.text);

    #[cfg(eagler_skins)]
    state.profile.skin.draw(
        p,
        ctx,
        x,
        92.0,
        WIDGET_WIDTH_BIG,
        [x - 118.0, 48.0, 108.0, bottom - 52.0],
    );

    let back = if state.profile.first_run {
        (entry_screen() != Screen::EditProfile).then(entry_screen)
    } else {
        Some(Screen::Accounts)
    };

    #[cfg(eagler_skins)]
    let (done_at, cancel_at) = {
        let half = (WIDGET_WIDTH_BIG - 4.0) / 2.0;
        let done_w = if back.is_some() {
            half
        } else {
            WIDGET_WIDTH_BIG
        };
        ([x, buttons, done_w], [x + half + 4.0, buttons, half])
    };
    #[cfg(not(eagler_skins))]
    let (done_at, cancel_at) = {
        let base = (ctx.vh / 4.0).floor();
        (
            [x, base + 114.0, WIDGET_WIDTH_BIG],
            [x, base + 138.0, WIDGET_WIDTH_BIG],
        )
    };
    let done = Button {
        x: done_at[0],
        y: done_at[1],
        w: done_at[2],
        h: WIDGET_HEIGHT,
        label: DONE,
        active: valid,
    };

    if (done.draw(p, ctx) || ctx.input.enter) && valid {
        state.profile.commit_form();
        leave(state, Screen::Accounts);
    }

    if let Some(back) = back {
        let label = if state.profile.first_run {
            BACK
        } else {
            CANCEL
        };
        let [cx, cy, cw] = cancel_at;
        let cancel = Button::new(cx, cy, cw, WIDGET_HEIGHT, label);
        if cancel.draw(p, ctx) || ctx.input.escape {
            state.nav = Some(back);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_footer_rows_line_up() {
        assert_eq!(BAR_W, 230.0);
        assert_eq!(2.0 * TOP_ROW_BUTTON_W + BUTTON_GAP, BAR_W);
        assert_eq!(3.0 * LOWER_ROW_BUTTON_W + 2.0 * BUTTON_GAP, BAR_W);
        assert_eq!(TOP_ROW_BUTTON_W.fract(), 0.0);
    }

    #[test]
    fn list_fills_between_header_and_footer() {
        let (x, y, w, h) = LAYOUT.content_rect(854.0, 480.0);
        assert_eq!((x, y, w), (0.0, 33.0, 854.0));
        assert_eq!(h, 480.0 - 33.0 - 60.0);
    }

    #[test]
    fn the_form_adds_and_renames() {
        let mut st = ProfileState {
            loaded: true,
            ..Default::default()
        };
        st.begin_add();
        st.username.set_text("Player");
        st.commit_form();
        assert_eq!(st.accounts.active, Some(0));
        assert_eq!(st.accounts.active().unwrap().name, "Player");

        st.begin_edit(0);
        st.username.set_text("Renamed");
        st.commit_form();
        assert_eq!(st.accounts.entries.len(), 1);
        assert_eq!(st.accounts.active().unwrap().name, "Renamed");
    }

    #[test]
    fn a_microsoft_account_cannot_be_edited() {
        let mut st = ProfileState {
            loaded: true,
            accounts: Accounts {
                entries: vec![Account {
                    name: "Notch".into(),
                    kind: AccountKind::Microsoft,
                    token: Some("refresh-token".into()),
                    uuid: Some("069a79f4-44e9-4726-a5be-fca90e38aaf5".into()),
                    session: Some("session-token".into()),
                    session_expires: Some(1_800_000_000),
                }],
                active: Some(0),
            },
            ..Default::default()
        };
        st.begin_edit(0);
        assert_eq!(st.editing, None);
    }
}

// ---------------------------------------------------------------------------------------
// Treasure Client: the Treasure Client profile screens, the same as the JS and WASM
// editions. Edit Profile (name, skin, capes) opens first; Profiles (the face button in
// the top right of the title screen) lists every profile. Each profile is a Torch
// account: its own name, skin and cape (and its own mod setup in the Mod Menu).
// ---------------------------------------------------------------------------------------

#[derive(Default)]
pub(crate) struct TcUi {
    skin_list: crate::gui::tc_style::Dropdown,
    cape_list: crate::gui::tc_style::Dropdown,
    pub(crate) capes: crate::gui::capepick::CapeList,
    cape_index: usize,
    choosing_model: bool,
    cape_rejected: bool,
    edit_parent: Option<Screen>,
    faces_key: String,
    scroll: f32,
}

#[cfg(not(eagler_skins))]
pub fn draw(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    torch_draw(p, state, ctx);
}

#[cfg(not(eagler_skins))]
pub fn draw_edit(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
    torch_draw_edit(p, state, ctx);
}

#[cfg(not(eagler_skins))]
pub fn draw_cape(_p: &mut Painter, state: &mut GuiState, _ctx: &ScreenCtx) {
    state.nav = Some(Screen::EditProfile);
}

#[cfg(eagler_skins)]
pub(crate) use tc::*;

#[cfg(eagler_skins)]
mod tc {
    use super::*;
    use crate::gui::tc_style;

    const CARD_W: f32 = 250.0;
    const CARD_H: f32 = 30.0;
    const CARD_GAP: f32 = 4.0;
    const MAX_PROFILES: usize = 20;
    const LABEL: u32 = 0xA0A0A0;

    /// Opens Edit Profile for the profile in use (a new one when there are none).
    pub(crate) fn open_edit(state: &mut GuiState, parent: Screen) {
        state.profile.open();
        match state.profile.accounts.active {
            Some(i) => state.profile.begin_edit(i),
            None => state.profile.begin_add(),
        }
        let name = state.profile.username.text.clone();
        state.profile.tc.capes.load();
        state.profile.tc.cape_index = state.profile.tc.capes.index_for(&name);
        state.profile.tc.choosing_model = false;
        state.profile.tc.skin_list.open = false;
        state.profile.tc.edit_parent = Some(parent);
        state.nav = Some(Screen::EditProfile);
    }

    /// Tells the Mod Menu which profile is in use, so each profile keeps its own mods.
    fn tell_overlay(state: &GuiState) {
        #[cfg(target_arch = "wasm32")]
        {
            let accounts = &state.profile.accounts;
            let names: Vec<&str> = accounts.entries.iter().map(|a| a.name.as_str()).collect();
            let active = accounts.active().map(|a| a.name.as_str()).unwrap_or("-1");
            crate::gui::tc_bridge::set_profile(active, &names);
        }
        #[cfg(not(target_arch = "wasm32"))]
        let _ = state;
    }

    /// Keeps the faces sheet (8x8 faces of every profile) up to date.
    fn update_faces(state: &mut GuiState) {
        let profile = &mut state.profile;
        profile.skin.ensure_loaded();
        let mut key = String::new();
        for a in &profile.accounts.entries {
            key.push_str(&a.name);
            key.push('=');
            key.push_str(profile.skin.label_for(&a.name).unwrap_or(""));
            key.push(';');
        }
        if key == profile.tc.faces_key {
            return;
        }
        let mut sheet = vec![0u8; 64 * 64 * 4];
        for (i, a) in profile.accounts.entries.iter().enumerate().take(64) {
            let Some(skin) = profile.skin.face_rgba(&a.name) else { continue };
            let (ox, oy) = ((i % 8) * 8, (i / 8) * 8);
            for y in 0..8 {
                for x in 0..8 {
                    let face = ((8 + y) * 64 + 8 + x) * 4;
                    let hat = ((8 + y) * 64 + 40 + x) * 4;
                    let src = if skin[hat + 3] > 0 { hat } else { face };
                    let dst = ((oy + y) * 64 + ox + x) * 4;
                    sheet[dst..dst + 3].copy_from_slice(&skin[src..src + 3]);
                    sheet[dst + 3] = 0xFF;
                }
            }
        }
        crate::gui::puppet::show_faces(&std::sync::Arc::new(sheet));
        profile.tc.faces_key = key;
        tell_overlay(state);
    }

    fn face(p: &mut Painter, index: usize, x: f32, y: f32, size: f32) {
        if index >= 64 {
            return;
        }
        let Some(sheet) = p.atlas.server_icon(crate::gui::atlas::PUPPET_FACES_SLOT) else {
            return;
        };
        let (sx, sy) = (((index % 8) * 8) as f32, ((index / 8) * 8) as f32);
        p.region_part(sheet, sx, sy, 8.0, 8.0, x, y, size, size, [1.0; 4]);
    }

    /// The face button in the top right of the title screen. Opens Profiles.
    pub(crate) fn title_button(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
        state.profile.ensure_loaded();
        update_faces(state);
        let (x, y, s) = (ctx.vw - 28.0, 6.0, 22.0);
        let hovered = ctx.hovering(x, y, s, s);
        let acc = tc_style::accent();
        let bg = if hovered { tc_style::lerp(tc_style::panel_bg(), acc, 0.25) } else { tc_style::panel_bg() };
        tc_style::rounded_rect(p, x, y, s, s, bg);
        tc_style::rounded_outline(p, x, y, s, s, if hovered { 0xFFFF_FFFF } else { acc });
        if let Some(active) = state.profile.accounts.active {
            face(p, active, x + 3.0, y + 3.0, 16.0);
        }
        if let Some(name) = state.profile.accounts.active().map(|a| a.name.clone()) {
            let w = p.atlas.font.width_str(&name);
            let color = if hovered { 0xFFFFFF } else { tc_style::text_idle() & 0x00FF_FFFF };
            p.text_plain(&name, x - 6.0 - w, y + 7.0, color, true);
        }
        if hovered {
            let tip = "Profiles";
            let tw = p.atlas.font.width_str(tip);
            let (tx, ty) = (x + s - tw - 4.0, y + s + 4.0);
            p.fill(tx - 3.0, ty - 2.0, tw + 6.0, 12.0, 0xE000_0000);
            p.text_plain(tip, tx, ty, acc & 0x00FF_FFFF, true);
        }
        if crate::gui::widgets::click_in(ctx, x, y, s, s) {
            #[cfg(feature = "audio")]
            crate::gui::widgets::play_click();
            state.profile.open();
            state.nav = Some(Screen::Accounts);
        }
    }

    /// The Profiles screen.
    pub fn draw(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
        state.profile.ensure_loaded();
        update_faces(state);
        menu::background(p, ctx.vw, ctx.vh);
        let cx = (ctx.vw / 2.0).floor();
        let acc = tc_style::accent();
        p.text_centered("Profiles", cx, 14.0, acc & 0x00FF_FFFF, true);
        p.text_centered(
            "Each profile has its own name, skin, cape and mods",
            cx,
            27.0,
            0xB8A888,
            true,
        );

        let confirming = state.profile.confirm_delete.is_some();
        let blocked_input;
        let ctx_ui = if confirming {
            let mut i = ctx.input.clone();
            i.mouse = None;
            i.left_click = false;
            i.scroll = 0.0;
            i.escape = false;
            blocked_input = i;
            ctx.with_input(&blocked_input)
        } else {
            ctx.with_input(ctx.input)
        };
        let ctx2 = &ctx_ui;

        let by = ctx.vh - 52.0;
        let (top, bottom) = (44.0, by - 8.0);
        let count = state.profile.accounts.entries.len();
        let content = count as f32 * (CARD_H + CARD_GAP);
        let max_scroll = (content - (bottom - top)).max(0.0);
        if ctx2.hovering(0.0, top, ctx.vw, bottom - top) && ctx2.input.scroll != 0.0 {
            state.profile.tc.scroll -= ctx2.input.scroll.signum() * (CARD_H + CARD_GAP);
        }
        state.profile.tc.scroll = state.profile.tc.scroll.clamp(0.0, max_scroll);
        let x = cx - CARD_W / 2.0;
        let active = state.profile.accounts.active;
        let mut clicked = None;
        for i in 0..count {
            let y = top + i as f32 * (CARD_H + CARD_GAP) - state.profile.tc.scroll;
            if y < top - 1.0 || y + CARD_H > bottom + 1.0 {
                continue;
            }
            let account = &state.profile.accounts.entries[i];
            let act = active == Some(i);
            let hover = ctx2.hovering(x, y, CARD_W, CARD_H);
            let bg = if hover && !act {
                tc_style::lerp(tc_style::panel_bg(), acc, 0.12)
            } else {
                tc_style::panel_bg()
            };
            tc_style::rounded_rect(p, x, y, CARD_W, CARD_H, bg);
            let outline = if act { acc } else if hover { 0x80FF_FFFF } else { 0x40FF_FFFF };
            tc_style::rounded_outline(p, x, y, CARD_W, CARD_H, outline);
            face(p, i, x + 5.0, y + 5.0, 20.0);
            let name_color = if act { acc & 0x00FF_FFFF } else { 0xFFFFFF };
            p.text_plain(&account.name, x + 32.0, y + 6.0, name_color, true);
            let mut sub = if act { "In use".to_owned() } else { "Click to use".to_owned() };
            if let Some(label) = state.profile.skin.label_for(&account.name) {
                sub.push_str(" \u{00B7} ");
                sub.push_str(label);
            }
            let sub_color = if act { tc_style::lerp(acc, 0xFF00_0000, 0.25) & 0x00FF_FFFF } else { 0x9A8F7A };
            p.text_plain(&sub, x + 32.0, y + 17.0, sub_color, false);
            if act {
                p.text_plain("\u{2714}", x + CARD_W - 12.0, y + 11.0, acc & 0x00FF_FFFF, true);
            }
            if hover && ctx2.input.left_click && !act {
                clicked = Some(i);
            }
        }
        if content > bottom - top {
            p.text_centered("\u{2195} scroll", cx + CARD_W / 2.0 + 26.0, bottom - 10.0, 0x6F6656, true);
        }
        if let Some(i) = clicked {
            #[cfg(feature = "audio")]
            crate::gui::widgets::play_click();
            state.profile.accounts.activate(i);
            state.profile.selected = Some(i);
            state.profile.save();
            state.profile.publish();
            tell_overlay(state);
        }

        if Button::new(cx - 152.0, by, 150.0, WIDGET_HEIGHT, "Edit Name & Skin").draw(p, ctx2) {
            open_edit(state, Screen::Accounts);
        }
        if Button::new(cx + 2.0, by, 150.0, WIDGET_HEIGHT, "Mod Settings").draw(p, ctx2) {
            #[cfg(target_arch = "wasm32")]
            crate::gui::tc_bridge::open_menu();
        }
        let new = Button {
            active: count < MAX_PROFILES,
            ..Button::new(cx - 152.0, by + 24.0, 98.0, WIDGET_HEIGHT, "New Profile")
        };
        if new.draw(p, ctx2) {
            let name = random_name(&state.profile.accounts);
            state.profile.accounts.add(Account {
                name,
                kind: AccountKind::Offline,
                ..Account::default()
            });
            state.profile.selected = state.profile.accounts.active;
            state.profile.save();
            state.profile.publish();
            tell_overlay(state);
            state.profile.tc.scroll = f32::MAX;
            open_edit(state, Screen::Accounts);
        }
        let delete = Button {
            active: count > 1,
            ..Button::new(cx - 50.0, by + 24.0, 98.0, WIDGET_HEIGHT, "Delete")
        };
        if delete.draw(p, ctx2) {
            state.profile.confirm_delete = state.profile.accounts.active;
        }
        if Button::new(cx + 54.0, by + 24.0, 98.0, WIDGET_HEIGHT, "Done").draw(p, ctx2)
            || ctx2.input.escape
        {
            state.nav = Some(Screen::Title);
        }

        if confirming {
            let name = state
                .profile
                .confirm_delete
                .and_then(|i| state.profile.accounts.get(i))
                .map(|a| a.name.clone());
            draw_delete_confirm(p, state, ctx);
            if state.profile.confirm_delete.is_none()
                && let Some(name) = name
                && !state.profile.accounts.entries.iter().any(|a| a.name == name)
            {
                state.profile.skin.forget(&name);
                state.profile.tc.capes.load();
                state.profile.tc.capes.forget(&name);
                tell_overlay(state);
            }
        }
    }

    fn random_name(accounts: &Accounts) -> String {
        let seed = crate::platform::time::SystemTime::now()
            .duration_since(crate::platform::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(7);
        for n in 0..1000u64 {
            let name = format!("Player{}", (seed / 7 + n * 37) % 900 + 100);
            if !accounts.entries.iter().any(|a| a.name == name) {
                return name;
            }
        }
        "Player".to_owned()
    }

    /// The Edit Profile screen: username, skin (with Add Skin / Clear List and a 3D
    /// preview) and a Capes link.
    pub fn draw_edit(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
        state.profile.ensure_loaded();
        state.profile.skin.ensure_loaded();
        state.profile.tc.capes.load();
        menu::background(p, ctx.vw, ctx.vh);
        let cx = (ctx.vw / 2.0).floor();
        let h6 = (ctx.vh / 6.0).floor();
        let acc = tc_style::accent();
        let title = if state.profile.editing.is_some() { "Edit Profile" } else { "New Profile" };
        p.text_centered(title, cx, 15.0, acc & 0x00FF_FFFF, true);

        if state.profile.skin.poll_upload() {
            state.profile.tc.choosing_model = true;
        }

        let overlay = state.profile.tc.skin_list.open || state.profile.tc.choosing_model;
        let blocked_input;
        let ctx_ui = if overlay {
            let mut i = ctx.input.clone();
            i.mouse = None;
            i.left_click = false;
            i.enter = false;
            blocked_input = i;
            ctx.with_input(&blocked_input)
        } else {
            ctx.with_input(ctx.input)
        };
        let ui = &ctx_ui;

        p.text_plain("Username", cx - 20.0, h6 + 8.0, LABEL, true);
        let field = &mut state.profile.username;
        field.update(p, ui, cx - 20.0, h6 + 24.0, 140.0, WIDGET_HEIGHT);
        // Like the original: only letters, numbers and _ (anything else becomes _).
        let clean: String = field
            .text
            .chars()
            .take(16)
            .map(|c| if c.is_ascii_alphanumeric() || c == '_' { c } else { '_' })
            .collect();
        if clean != field.text {
            field.set_text(&clean);
        }
        let valid = valid_username(field.text.trim());

        p.text_plain("Player Skin", cx - 20.0, h6 + 66.0, LABEL, true);
        let names = state.profile.skin.names();
        let index = state.profile.skin.index();
        let was_open = state.profile.tc.skin_list.open;
        state.profile.tc.skin_list.draw_box(
            p,
            if state.profile.tc.choosing_model { ui } else { ctx },
            cx - 20.0,
            h6 + 82.0,
            names.get(index).map(String::as_str).unwrap_or(""),
        );
        if !was_open && state.profile.tc.skin_list.open {
            state.profile.tc.skin_list.show(index);
        }

        if Button::new(cx - 21.0, h6 + 110.0, 71.0, WIDGET_HEIGHT, "Add Skin").draw(p, ui) {
            state.profile.skin.choose_file();
        }
        if Button::new(cx + 50.0, h6 + 110.0, 72.0, WIDGET_HEIGHT, "Clear List").draw(p, ui) {
            state.profile.skin.clear_uploads();
        }
        if state.profile.skin.rejected() {
            p.text_centered(
                "That file is not a 64x64 or 64x32 skin PNG",
                cx + 50.0,
                h6 + 134.0,
                0xFF5555,
                true,
            );
        }

        // The preview: the skin on a 3D figure that looks at the mouse.
        let (fx, fy, fw, fh) = (cx - 120.0, h6 + 8.0, 80.0, 130.0);
        tc_style::frame(p, fx, fy, fw, fh);
        if !state.profile.tc.choosing_model {
            let slim = state.profile.skin.slim();
            state.profile.skin.preview_as(p, ui, [fx + 2.0, fy + 12.0, fw - 4.0, fh - 16.0], slim);
        }
        if state.profile.skin.uploaded() && ui.hovering(fx, fy + 10.0, fw, fh - 10.0) {
            p.text_centered("Click for Steve / Alex", fx + fw / 2.0, fy + fh + 3.0, 0x9A8F7A, false);
        }
        if state.profile.skin.uploaded() && crate::gui::widgets::click_in(ui, fx, fy + 10.0, fw, fh - 10.0) {
            state.profile.tc.choosing_model = true;
        }
        if tc_style::link(p, ui, "Capes", fx + 2.0, fy - 9.0, 0.75) {
            state.profile.tc.cape_list.open = false;
            state.profile.tc.cape_rejected = false;
            state.nav = Some(Screen::EditCape);
        }

        let done = Button {
            active: valid,
            ..Button::new(cx - 100.0, h6 + 168.0, 200.0, WIDGET_HEIGHT, "Done")
        };
        if (done.draw(p, ui) || ui.input.enter) && valid {
            let old = state
                .profile
                .editing
                .and_then(|i| state.profile.accounts.get(i))
                .map(|a| a.name.clone());
            let name = state.profile.username.text.trim().to_owned();
            state.profile.commit_form();
            let cape = state.profile.tc.cape_index;
            state.profile.tc.capes.pick(old.as_deref(), &name, cape);
            tell_overlay(state);
            let back = state.profile.tc.edit_parent.take().unwrap_or(Screen::Title);
            leave(state, back);
            return;
        }
        if ctx.input.escape && !overlay && !state.profile.first_run {
            state.nav = Some(state.profile.tc.edit_parent.take().unwrap_or(Screen::Title));
        }

        // On top: the skin list, or the Steve / Alex choice after adding a skin.
        let picked = state.profile.tc.skin_list.draw_list(
            p,
            ctx,
            cx - 20.0,
            h6 + 82.0,
            &names,
            index,
        );
        if let Some(i) = picked {
            state.profile.skin.set_index(i);
        }
        if ctx.input.escape && state.profile.tc.skin_list.open {
            state.profile.tc.skin_list.open = false;
        }
        if state.profile.tc.choosing_model {
            choose_model(p, state, ctx);
        }
    }

    /// After adding a skin: pick the Steve (wide arms) or Alex (slim arms) model.
    fn choose_model(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
        p.fill(0.0, 0.0, ctx.vw, ctx.vh, 0xBB00_0000);
        let cx = (ctx.vw / 2.0).floor();
        let top = (ctx.vh / 4.0).floor();
        p.text_centered("Which model is this skin for?", cx, top - 16.0, 0xFFFFFF, true);
        for (slim, x, label) in [(false, cx - 90.0, "Steve"), (true, cx + 20.0, "Alex")] {
            let hovered = ctx.hovering(x, top, 70.0, 120.0);
            let color = if hovered { 0xFFDD_DD99 } else { 0xFF55_5555 };
            p.fill(x, top, 70.0, 120.0, 0xBB00_0000);
            tc_style::rounded_outline(p, x, top, 70.0, 120.0, color);
            state.profile.skin.preview_as(p, ctx, [x + 2.0, top + 4.0, 66.0, 112.0], slim);
            if hovered {
                p.text_centered(label, x + 35.0, top + 126.0, color & 0x00FF_FFFF, true);
            }
            if crate::gui::widgets::click_in(ctx, x, top, 70.0, 120.0) {
                state.profile.skin.set_slim(slim);
                state.profile.tc.choosing_model = false;
            }
        }
        if ctx.input.escape {
            state.profile.tc.choosing_model = false;
        }
    }

    /// The Edit Cape screen.
    pub fn draw_cape(p: &mut Painter, state: &mut GuiState, ctx: &ScreenCtx) {
        state.profile.tc.capes.load();
        menu::background(p, ctx.vw, ctx.vh);
        let cx = (ctx.vw / 2.0).floor();
        let h6 = (ctx.vh / 6.0).floor();
        let acc = tc_style::accent();
        p.text_centered("Edit Cape", cx, 15.0, acc & 0x00FF_FFFF, true);

        #[cfg(target_arch = "wasm32")]
        if let Some((file, bytes)) = crate::platform::upload::take_png() {
            match state.profile.tc.capes.add(&file, &bytes) {
                Some(i) => {
                    state.profile.tc.cape_index = i;
                    state.profile.tc.cape_rejected = false;
                }
                None => state.profile.tc.cape_rejected = true,
            }
        }

        let open = state.profile.tc.cape_list.open;
        let blocked_input;
        let ctx_ui = if open {
            let mut i = ctx.input.clone();
            i.mouse = None;
            i.left_click = false;
            i.enter = false;
            blocked_input = i;
            ctx.with_input(&blocked_input)
        } else {
            ctx.with_input(ctx.input)
        };
        let ui = &ctx_ui;

        let capes = &mut state.profile.tc.capes;
        let names = capes.names();
        let index = state.profile.tc.cape_index.min(capes.len() - 1);
        p.text_plain("Player Cape", cx - 20.0, h6 + 36.0, LABEL, true);
        state.profile.tc.cape_list.draw_box(p, ctx, cx - 20.0, h6 + 52.0, &names[index]);
        if !open && state.profile.tc.cape_list.open {
            state.profile.tc.cape_list.show(index);
        }
        if Button::new(cx - 21.0, h6 + 80.0, 71.0, WIDGET_HEIGHT, "Add Cape").draw(p, ui) {
            #[cfg(target_arch = "wasm32")]
            crate::platform::upload::choose_png();
        }
        if Button::new(cx + 50.0, h6 + 80.0, 72.0, WIDGET_HEIGHT, "Clear List").draw(p, ui) {
            let none = state.profile.tc.capes.custom.len();
            let was_custom = index < none;
            state.profile.tc.capes.clear_custom();
            state.profile.tc.cape_index = if was_custom { 0 } else { index - none };
        }
        if state.profile.tc.cape_rejected {
            p.text_centered(
                "Capes must be 64x32 or 32x32 PNGs",
                cx + 50.0,
                h6 + 104.0,
                0xFF5555,
                true,
            );
        }

        // The preview: the cape's outside, big.
        let (fx, fy, fw, fh) = (cx - 120.0, h6 + 8.0, 80.0, 130.0);
        tc_style::frame(p, fx, fy, fw, fh);
        let capes = &state.profile.tc.capes;
        let index = state.profile.tc.cape_index.min(capes.len() - 1);
        if capes.is_none(index) {
            p.text_centered("No Cape", fx + fw / 2.0, fy + fh / 2.0 - 4.0, 0x808080, true);
        } else if let Some(rgba) = capes.rgba(index) {
            crate::gui::puppet::show_cape(&rgba);
            if let Some(slot) = p.atlas.server_icon(crate::gui::atlas::PUPPET_CAPE_SLOT) {
                let (w, h) = (50.0, 80.0);
                p.region_part(slot, 1.0, 1.0, 10.0, 16.0, fx + (fw - w) / 2.0, fy + (fh - h) / 2.0, w, h, [1.0; 4]);
            }
        }

        if Button::new(cx - 100.0, h6 + 168.0, 200.0, WIDGET_HEIGHT, "Done").draw(p, ui)
            || (ctx.input.escape && !open)
        {
            state.nav = Some(Screen::EditProfile);
        }

        let names = state.profile.tc.capes.names();
        if let Some(i) = state.profile.tc.cape_list.draw_list(p, ctx, cx - 20.0, h6 + 52.0, &names, index) {
            state.profile.tc.cape_index = i;
        }
        if ctx.input.escape && open {
            state.profile.tc.cape_list.open = false;
        }
    }
}
