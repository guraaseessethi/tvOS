sub init()
    m.th = LumenTheme()
    m.reg = CreateObject("roRegistrySection", "lumen")
    m.theme = m.reg.Read("theme")
    if m.th.themes[m.theme] = invalid then m.theme = "starry"
    deep = m.th.themes[m.theme].deep
    m.top.backgroundColor = deep
    ' Same painting as the splash, so the first frame matches before the layers load.
    m.top.backgroundURI = "pkg:/images/wall/wall_" + m.theme + "_home.jpg"

    ' 1-4: wallpaper, blur, scrims
    m.wall = m.top.createChild("WallpaperLayer")
    m.wall.theme = m.theme

    ' 5: content
    m.clock = m.top.createChild("ClockBlock")
    m.clock.deep = deep
    m.clock.clock24 = (m.reg.Read("clock24") = "true")

    m.hello = m.top.createChild("HelloCard")
    m.hello.deep = deep
    m.hello.name = m.reg.Read("userName")

    m.credit = m.top.createChild("PaintingCredit")
    m.credit.theme = m.theme

    m.row = m.top.createChild("AppRow")
    m.row.deep = deep

    m.bottom = m.top.createChild("BottomBar")
    m.bottom.deep = deep

    ' Spec 6.3: [Ask Zeus] [gear], gear flush right at x=1824, 14px gap.
    m.gear = m.top.createChild("FrostPill")
    m.gear.deep = deep
    m.gear.round = true
    m.gear.solid = true
    m.gear.glyph = "pkg:/images/ui/ic_gear.png"
    m.gear.translation = [1824 - 64, 72]
    m.askZeus = m.top.createChild("FrostPill")
    m.askZeus.deep = deep
    m.askZeus.style = "yellow"
    m.askZeus.solid = true
    m.askZeus.glyph = "pkg:/images/brand/zeus_mark_48.png"
    m.askZeus.text = "Ask Zeus"
    m.askZeus.translation = [1824 - 64 - 14 - m.askZeus.width, 72]
    m.topItems = [m.askZeus, m.gear]

    ' Games card in the open space top-right, above the row.
    m.gamesCard = m.top.createChild("GamesCard")
    m.gamesCard.deep = deep
    m.gamesCard.theme = m.theme
    ' Spans from just right of the clock to the right edge (width 1241 from GamesCard).
    m.gamesCard.translation = [583, 160]

    ' 6: overlays
    m.memories = m.top.createChild("MemoriesView")
    m.photos = []
    m.picker = m.top.createChild("AppPicker")
    m.picker.deep = deep
    m.hidden = ParseJson(m.reg.Read("hiddenApps"))
    if m.hidden = invalid then m.hidden = {}
    m.allApps = []
    m.sleepSheet = m.top.createChild("Sheet")
    m.sleepSheet.title = "Sleep timer"
    m.sleepSheet.subtitle = "The TV turns itself off, even inside an app"
    m.sleepSheet.observeField("chosen", "onSleepChosen")
    m.settingsSheet = m.top.createChild("Sheet")
    m.settingsSheet.title = "Settings"
    m.settingsSheet.subtitle = "OK to change   ·   Back to close"
    m.settingsSheet.observeField("changed", "onSettingChanged")
    m.settingsSheet.observeField("chosen", "onSettingChanged")
    m.sleepMin = 0
    m.editing = false
    m.editHint = makeHint()
    m.zeus = m.top.createChild("ZeusPanel")
    m.zeus.deep = deep
    m.zeus.observeField("ask", "onZeusAsk")
    m.toast = m.top.createChild("Toast")
    m.launch = m.top.createChild("LaunchOverlay")
    m.launch.deep = deep
    for each sh in [m.sleepSheet, m.settingsSheet]
        sh.deep = deep
    end for
    m.appsLoaded = false
    m.bridge = BridgeBase()
    m.row.iconBase = m.bridge
    m.launch.iconBase = m.bridge
    m.hello.bridge = m.bridge

    ' Focus state (spec 11): one zone at a time, each zone remembers its index.
    m.zone = "row"
    m.topIndex = 0
    m.overlay = invalid
    applyFocus()

    m.reqId = 0
    m.tasks = {}
    applyCache()
    loadApps()

    m.launchTimeout = m.top.createChild("Timer")
    m.launchTimeout.duration = 5
    m.launchTimeout.observeField("fire", "onLaunchTimeout")

    playIntro()
    m.top.setFocus(true)
end sub

' After the splash (the same painting, so the wallpaper itself never animates), the UI arrives
' in three light stages. The intro waits briefly so the first textures finish decoding first;
' starting during decoding was what made it stutter.
sub playIntro()
    m.clock.id = "introClock"
    m.hello.id = "introHello"
    m.credit.id = "introCredit"
    m.gamesCard.id = "introGames"
    m.askZeus.id = "introZeus"
    m.gear.id = "introGear"
    m.row.id = "introRow"
    m.bottom.id = "introBottom"
    for each n in [m.clock, m.hello, m.credit, m.gamesCard, m.askZeus, m.gear, m.row, m.bottom]
        n.opacity = 0
    end for
    m.intro = []
    ' Quick and crisp: ~0.5s total, short travel, small stagger.
    introStep(0.0, 0.28, [["introClock", [0, 8], [0, 0]], ["introHello", invalid, invalid], ["introCredit", invalid, invalid]])
    introStep(0.06, 0.28, [["introGames", invalid, invalid], ["introZeus", invalid, invalid], ["introGear", invalid, invalid]])
    introStep(0.12, 0.32, [["introRow", [0, m.th.rowY + 16], [0, m.th.rowY]], ["introBottom", invalid, invalid]])
    m.introWait = m.top.createChild("Timer")
    m.introWait.duration = 0.15
    m.introWait.observeField("fire", "startIntro")
    m.introWait.control = "start"
end sub

sub startIntro()
    for each a in m.intro
        a.control = "start"
    end for
end sub

' One stage: fade the listed nodes in (and slide them from/to when given), after a delay.
sub introStep(delay as float, duration as float, nodes as object)
    a = m.top.createChild("Animation")
    a.delay = delay
    a.duration = duration
    a.easeFunction = "outQuad"
    for each n in nodes
        f = a.createChild("FloatFieldInterpolator")
        f.fieldToInterp = n[0] + ".opacity"
        f.key = [0, 1]
        f.keyValue = [0, 1]
        if n[1] <> invalid
            v = a.createChild("Vector2DFieldInterpolator")
            v.fieldToInterp = n[0] + ".translation"
            v.key = [0, 1]
            v.keyValue = [n[1], n[2]]
        end if
    end for
    m.intro.Push(a)
end sub

' Draw the row from the last known app list right away; the bridge refreshes it moments later.
sub applyCache()
    sec = CreateObject("roRegistrySection", "lumen")
    m.appsJson = sec.Read("appsCache")
    if m.appsJson <> invalid and m.appsJson <> ""
        data = ParseJson(m.appsJson)
        if data <> invalid then applyApps(data.apps, data.inputs)
    end if
    isTV = sec.Read("isTV")
    if isTV = "false" then m.bottom.isTV = false
    photos = ParseJson(sec.Read("photosCache"))
    if photos <> invalid then applyPhotos(photos)
end sub

sub applyApps(apps as object, inputs as object)
    m.allApps = apps
    shown = []
    for each a in apps
        if m.hidden[a.id] = invalid then shown.Push(a)
    end for
    m.row.apps = shown
    m.bottom.inputs = inputs
    m.bottom.rebuild = true
    applyFocus()
end sub

sub applyPhotos(photos as object)
    m.photos = photos
    m.row.photos = photos
    m.memories.photos = photos
end sub

' Picture button / Settings: switch the painting everywhere (spec 8.3 repaint).
sub applyTheme(key as string)
    if m.th.themes[key] = invalid or key = m.theme then return
    m.theme = key
    deep = m.th.themes[key].deep
    m.wall.theme = key
    m.top.backgroundColor = deep
    m.top.backgroundURI = "pkg:/images/wall/wall_" + key + "_home.jpg"
    m.credit.theme = key
    m.gamesCard.theme = key
    for each n in [m.clock, m.hello, m.row, m.bottom, m.gear, m.askZeus, m.gamesCard, m.launch, m.sleepSheet, m.settingsSheet, m.picker, m.zeus]
        n.deep = deep
    end for
    m.reg.Write("theme", key)
    m.reg.Flush()
end sub

function themeKeys() as object
    return ["starry", "sunflower", "irises", "blossom", "snow"]
end function

sub nextTheme()
    keys = themeKeys()
    i = 0
    for k = 0 to keys.Count() - 1
        if keys[k] = m.theme then i = k
    end for
    applyTheme(keys[(i + 1) mod keys.Count()])
    m.toast.message = m.th.themes[m.theme].painting
end sub

sub openSheet(sh as object)
    m.sheet = sh
    m.overlay = "sheet"
    sh.open = true
end sub

sub closeSheet()
    if m.sheet <> invalid then m.sheet.open = false
    m.sheet = invalid
    m.overlay = invalid
end sub

' Spec 7 + 9: Zeus. The bridge asks Gemini; the TV shows the reply and runs the actions.
sub openZeus()
    m.overlay = "zeus"
    m.zeus.open = true
end sub

sub closeZeus()
    m.zeus.open = false
    m.overlay = invalid
end sub

sub onZeusAsk()
    text = m.zeus.ask
    hist = m.zeus.callFunc("history")
    m.zeus.callFunc("addMessage", "user", text)
    if m.bridge = ""
        m.zeus.callFunc("addMessage", "zeus", "I need the Lumen helper running before I can help.")
        return
    end if
    m.zeus.thinking = true
    now = CreateObject("roDateTime")
    now.ToLocalTime()
    mins = now.GetMinutes().ToStr()
    if Len(mins) < 2 then mins = "0" + mins
    stamp = now.GetYear().ToStr() + "-" + now.GetMonth().ToStr() + "-" + now.GetDayOfMonth().ToStr() + " " + now.GetHours().ToStr() + ":" + mins
    body = FormatJson({ message: text, context: { theme: m.theme, localTime: stamp, history: hist } })
    ecp({ kind: "zeus", body: body })
end sub

sub onZeusAnswer(res as object)
    m.zeus.thinking = false
    if not res.ok
        m.zeus.callFunc("addMessage", "zeus", "I can't reach the Lumen helper right now. Try again in a moment.")
        return
    end if
    z = res.zeus
    if z.reply <> invalid and z.reply <> "" then m.zeus.callFunc("addMessage", "zeus", z.reply)
    if z.chips <> invalid and z.chips.Count() > 0 then m.zeus.callFunc("setChips", z.chips)
    if z.actions <> invalid then runZeusActions(z.actions)
end sub

sub runZeusActions(actions as object)
    for each a in actions
        t = a.type
        if t = "launch_app" or t = "switch_input"
            id = a.appId
            if t = "switch_input" then id = a.inputId
            target = invalid
            for each x in m.allApps
                if x.id = id then target = x
            end for
            if m.bottom.inputs <> invalid
                for each x in m.bottom.inputs
                    if x.id = id then target = x
                end for
            end if
            if target <> invalid
                closeZeus()
                launchApp(target)
            end if
        else if t = "keypress"
            n = 1
            if a.repeat <> invalid then n = a.repeat
            for i = 1 to n
                ecp({ kind: "keypress", key: a.key })
            end for
        else if t = "power"
            ecp({ kind: "keypress", key: "PowerOff" })
        else if t = "set_theme"
            applyTheme(a.theme)
        else if t = "set_sleep_timer"
            m.sleepMin = a.minutes
            ecp({ kind: "sleep", minutes: a.minutes })
        else if t = "cancel_sleep_timer"
            m.sleepMin = 0
            ecp({ kind: "sleep", minutes: 0 })
        else if t = "show_message"
            m.toast.message = a.text
        end if
    end for
end sub

sub openSleep()
    rows = []
    for each mins in [0, 15, 30, 60, 90]
        if mins = 0 then label = "Off" else label = mins.ToStr() + " minutes"
        if mins = 60 then label = "1 hour"
        note = ""
        if mins = m.sleepMin then note = "Current"
        rows.Push({ id: "sleep", label: label, value: mins, note: note })
    end for
    m.sleepSheet.rows = rows
    openSheet(m.sleepSheet)
end sub

sub onSleepChosen()
    mins = m.sleepSheet.chosen.value
    m.sleepMin = mins
    ecp({ kind: "sleep", minutes: mins })
    closeSheet()
    if mins = 0
        m.toast.message = "Sleep timer off"
    else
        t = CreateObject("roDateTime")
        t.FromSeconds(t.AsSeconds() + mins * 60)
        t.ToLocalTime()
        h = t.GetHours() mod 12
        if h = 0 then h = 12
        mm = t.GetMinutes().ToStr()
        if Len(mm) < 2 then mm = "0" + mm
        m.toast.message = "Sleep timer set for " + h.ToStr() + ":" + mm
    end if
end sub

sub openSettings()
    paintings = []
    for each k in themeKeys()
        paintings.Push({ label: m.th.themes[k].painting, value: k })
    end for
    clock = "12"
    if m.clock.clock24 then clock = "24"
    helper = "Not connected"
    if m.appsLoaded = true then helper = "Connected"
    m.settingsSheet.rows = [
        { id: "painting", label: "Painting", value: m.theme, choices: paintings }
        { id: "clock", label: "Clock", value: clock, choices: [{ label: "12-hour", value: "12" }, { label: "24-hour", value: "24" }] }
        { id: "helper", label: "Lumen helper", note: helper }
        { id: "about", label: "About", note: "Lumen 1.0" }
    ]
    openSheet(m.settingsSheet)
end sub

sub onSettingChanged(event as object)
    c = event.getData()
    if c.id = "painting" and c.value <> invalid
        applyTheme(c.value)
    else if c.id = "clock" and c.value <> invalid
        m.clock.clock24 = (c.value = "24")
        m.reg.Write("clock24", (c.value = "24").ToStr())
        m.reg.Flush()
    end if
end sub

' Edit Home's instruction card: plain step-by-step words over the (dimmed) games panel.
function makeHint() as object
    g = m.top.createChild("Group")
    g.translation = [663, 290]
    bg = g.createChild("Poster")
    bg.uri = "pkg:/images/ui/sheet_r44.9.png"
    bg.blendColor = m.th.paper
    bg.width = 1080
    bg.height = 170
    m.editTitle = g.createChild("Label")
    m.editTitle.font = LumenFont("Fraunces-Title", 40)
    m.editTitle.color = m.th.ink
    m.editTitle.translation = [44, 30]
    m.editTitle.width = 1000
    m.editTitle.height = 54
    m.editStep = g.createChild("Label")
    m.editStep.font = LumenFont("Manrope-Bold", 26)
    m.editStep.color = m.th.ink60
    m.editStep.translation = [46, 96]
    m.editStep.width = 1000
    m.editStep.height = 40
    g.visible = false
    return g
end function

sub editHintText()
    if m.row.lifted
        app = m.row.callFunc("current")
        name = "this card"
        if app <> invalid then name = app.name
        m.editTitle.text = "Moving " + name
        m.editStep.text = "Press Left or Right to move it, then OK to put it down"
    else
        m.editTitle.text = "Rearrange your apps"
        m.editStep.text = "Choose the card you want to move, then press OK.   Back when you're done."
    end if
end sub

sub startEdit()
    m.editing = true
    m.zone = "row"
    m.row.lifted = false
    m.gamesCard.opacity = 0.2
    m.hello.opacity = 0.4
    editHintText()
    m.editHint.visible = true
    applyFocus()
end sub

sub endEdit()
    m.editing = false
    m.row.lifted = false
    m.editHint.visible = false
    m.gamesCard.opacity = 1
    m.hello.opacity = 1
    m.toast.message = "Home saved"
end sub

sub loadApps()
    if m.bridge = ""
        m.toast.message = "Waiting for the Lumen helper…"
        return
    end if
    ecp({ kind: "device" })
    ecp({ kind: "apps" })
end sub

' The bridge announces itself with POST /input?bridge=<url> (spec 15.6 hello, PLAN bridge).
sub onInput()
    args = m.top.inputArgs
    if args = invalid then return
    ' Phone status from the bridge (spec 15.6 hello): connected, name, current one-time QR code.
    if args.phone <> invalid
        data = ParseJson(args.phone)
        if data <> invalid
            if data.name <> invalid and data.name <> "" then setUserName(data.name)
            if data.code <> invalid then m.hello.code = data.code
            if data.connected = true and not m.hello.connected and m.hello.name <> ""
                m.toast.message = "Phone connected. Hi, " + m.hello.name
            end if
            m.hello.connected = (data.connected = true)
        end if
    end if
    if args.bridge = invalid then return
    url = args.bridge
    if Left(url, 7) <> "http://" then return
    if url = m.bridge and m.appsLoaded = true then return
    m.bridge = url
    SaveBridgeBase(url)
    m.row.iconBase = url
    m.launch.iconBase = url
    m.hello.bridge = url
    loadApps()
end sub

sub setUserName(name as string)
    name = Left(name, 24)
    if name = m.hello.name then return
    m.hello.name = name
    m.reg.Write("userName", name)
    m.reg.Flush()
end sub

sub ecp(req as object)
    m.reqId = m.reqId + 1
    req.id = m.reqId
    req.base = m.bridge
    t = CreateObject("roSGNode", "EcpTask")
    t.request = req
    t.observeField("response", "onEcp")
    m.tasks[m.reqId.ToStr()] = t
    t.control = "RUN"
end sub

sub onEcp(event as object)
    res = event.getData()
    m.tasks.Delete(res.id.ToStr())
    n = 0
    if res.apps <> invalid then n = res.apps.Count()
    print "[lumen] ecp "; res.kind; " ok="; res.ok; " code="; res.code; " apps="; n
    if res.kind = "device"
        if res.ok
            m.bottom.isTV = (res.device["is-tv"] = "true")
            sec = CreateObject("roRegistrySection", "lumen")
            sec.Write("isTV", res.device["is-tv"])
            sec.Flush()
        end if
    else if res.kind = "apps"
        apps = []
        inputs = []
        if res.ok
            for each a in res.apps
                if a.type = "tvin"
                    inputs.Push(a)
                else if a.type = "appl"
                    apps.Push(a)
                end if
            end for
            m.appsLoaded = true
        else
            m.toast.message = "Can't reach the Lumen helper"
            return
        end if
        ecp({ kind: "memories" })
        ' Only rebuild the row when the list actually changed (keeps focus and icons steady).
        json = FormatJson({ apps: apps, inputs: inputs })
        if json <> m.appsJson
            m.appsJson = json
            applyApps(apps, inputs)
            sec = CreateObject("roRegistrySection", "lumen")
            sec.Write("appsCache", json)
            sec.Flush()
        end if
    else if res.kind = "memories"
        if res.ok
            json = FormatJson(res.photos)
            sec = CreateObject("roRegistrySection", "lumen")
            if json <> sec.Read("photosCache")
                applyPhotos(res.photos)
                sec.Write("photosCache", json)
                sec.Flush()
            end if
        end if
    else if res.kind = "launch"
        if res.id <> m.launchReq then return
        if res.ok
            ' The other app takes over; if it hasn't within 5s, drop the overlay.
            m.launchTimeout.control = "start"
        else
            if res.code = 403
                reason = "Turn on Settings > System > Advanced system settings > Control by mobile apps"
            else if res.code < 0 or res.code = 502
                reason = "Can't reach the Lumen helper. Is it running on your Mac or phone?"
            else
                reason = "The TV said no (" + res.code.ToStr() + "). Try again in a moment."
            end if
            m.launch.reason = reason
            m.launch.state = "error"
        end if
    else if res.kind = "zeus"
        onZeusAnswer(res)
    else if res.kind = "keypress"
        if not res.ok then m.toast.message = "The TV didn't respond"
    end if
end sub

sub onLaunchTimeout()
    closeOverlay()
end sub

sub launchApp(app as object)
    ' Send the launch first; the "Opening…" card is drawn while the TV switches.
    m.launchTimeout.control = "stop"
    ecp({ kind: "launch", appId: app.id })
    m.launchReq = m.reqId
    m.launch.app = app
    m.launch.state = "launching"
    m.overlay = "launch"
    m.lastLaunch = app
end sub

sub closeOverlay()
    m.launchTimeout.control = "stop"
    m.launch.state = "hidden"
    m.overlay = invalid
end sub

sub applyFocus()
    m.row.active = (m.zone = "row")
    m.bottom.active = (m.zone = "bottom")
    m.gamesCard.focused = (m.zone = "games")
    for i = 0 to m.topItems.Count() - 1
        m.topItems[i].focused = (m.zone = "top" and i = m.topIndex)
    end for
end sub

function topCenterX() as float
    n = m.topItems[m.topIndex]
    return n.translation[0] + n.width / 2
end function

function activate() as void
    if m.zone = "top"
        if m.topIndex = 0
            openZeus()
        else
            openSettings()
        end if
    else if m.zone = "row"
        app = m.row.callFunc("pressFocused")
        if app = invalid then return
        if app.id = "lumen.memories"
            if m.photos.Count() > 0
                m.memories.open = true
                m.overlay = "memories"
            else
                m.toast.message = "Add photos to Pictures › Lumen Memories"
            end if
        else if app.id = "lumen.add"
            m.picker.iconBase = m.bridge
            m.picker.apps = m.allApps
            m.picker.hidden = m.hidden
            m.picker.open = true
            m.overlay = "picker"
        else
            launchApp(app)
        end if
    else if m.zone = "bottom"
        item = m.bottom.callFunc("current")
        if item = invalid then return
        if item.kind = "input"
            launchApp({ id: item.id, name: item.name, type: "tvin" })
        else if item.kind = "off"
            ecp({ kind: "keypress", key: "PowerOff" })
        else if item.kind = "picture"
            nextTheme()
        else if item.kind = "sleep"
            openSleep()
        else if item.kind = "edit"
            startEdit()
        end if
    end if
end function

function onKeyEvent(key as string, press as boolean) as boolean
    if not press
        ' Releases matter only to the games panel (hold ‹ › in Star Catcher).
        if m.zone = "games" and m.overlay = invalid then m.gamesCard.callFunc("handleKey", key + "_up")
        return true
    end if

    if m.overlay = "memories"
        if key = "back"
            m.memories.open = false
            m.overlay = invalid
        else
            m.memories.callFunc("handleKey", key)
        end if
        return true
    end if

    if m.overlay = "launch"
        if key = "back"
            closeOverlay()
        else if m.launch.state = "error"
            if key = "left" or key = "right"
                m.launch.buttonIndex = 1 - m.launch.buttonIndex
            else if key = "OK"
                if m.launch.buttonIndex = 0 and m.lastLaunch <> invalid
                    launchApp(m.lastLaunch)
                else
                    closeOverlay()
                end if
            end if
        end if
        return true
    end if

    if m.overlay = "zeus"
        if key = "back" then closeZeus() else m.zeus.callFunc("handleKey", key)
        return true
    end if

    if m.overlay = "picker"
        if key = "back"
            m.picker.open = false
            m.overlay = invalid
            m.hidden = m.picker.hidden
            m.reg.Write("hiddenApps", FormatJson(m.hidden))
            m.reg.Flush()
            applyApps(m.allApps, m.bottom.inputs)
            m.toast.message = "Home updated"
        else
            m.picker.callFunc("handleKey", key)
        end if
        return true
    end if

    if m.overlay = "sheet"
        if not m.sheet.callFunc("handleKey", key) then closeSheet()
        return true
    end if

    if m.editing
        if key = "back"
            endEdit()
        else if key = "OK"
            m.row.lifted = not m.row.lifted
            editHintText()
        else if (key = "left" or key = "right") and m.row.lifted
            d = 1
            if key = "left" then d = -1
            m.row.lifted = false
            m.row.callFunc("moveFocused", d)
            m.row.lifted = true
        else if key = "left" or key = "right"
            d = 1
            if key = "left" then d = -1
            m.row.focusIndex = Max(0, Min(m.row.count - 2, m.row.focusIndex + d))
        end if
        return true
    end if

    if m.zone = "games" and m.gamesCard.callFunc("handleKey", key) then return true

    if key = "OK" or (key = "play" and m.zone = "row")
        activate()
    else if key = "back"
        m.toast.message = "You're home"
    else if key = "options"
        openZeus()
    else if key = "left" or key = "right"
        d = 1
        if key = "left" then d = -1
        if m.zone = "top"
            m.topIndex = Max(0, Min(m.topItems.Count() - 1, m.topIndex + d))
        else if m.zone = "row"
            m.row.focusIndex = Max(0, Min(m.row.count - 1, m.row.focusIndex + d))
        else if m.zone = "bottom"
            m.bottom.focusIndex = Max(0, Min(m.bottom.count - 1, m.bottom.focusIndex + d))
        end if
        applyFocus()
    else if key = "up"
        if m.zone = "games"
            m.zone = "top"
            m.topIndex = 0
        else if m.zone = "row" and m.row.callFunc("focusCenterX") >= 583
            m.zone = "games"
        else if m.zone = "row"
            m.zone = "top"
            m.topIndex = 0
        else if m.zone = "bottom"
            m.zone = "row"
            m.row.focusIndex = m.row.callFunc("indexNearestX", m.bottom.callFunc("focusCenterX"))
        end if
        applyFocus()
    else if key = "down"
        if m.zone = "top"
            m.zone = "games"
        else if m.zone = "games"
            m.zone = "row"
            m.row.focusIndex = m.row.callFunc("indexNearestX", 1203)
        else if m.zone = "row" and m.bottom.count > 0
            m.zone = "bottom"
            m.bottom.focusIndex = m.bottom.callFunc("indexNearestX", m.row.callFunc("focusCenterX"))
        end if
        applyFocus()
    end if
    return true
end function

function Max(a as integer, b as integer) as integer
    if a > b then return a
    return b
end function

function Min(a as integer, b as integer) as integer
    if a < b then return a
    return b
end function
