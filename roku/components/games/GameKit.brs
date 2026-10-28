' Shared game loop for the games panel. Each game defines reset(), begin(), advance(dt) and
' action(key), and has the interface fields theme, deep, key, state, points, best, newBest.
'
' Keys from the panel: "OK" (start / act / resume / play again), "pause", "stop",
' and "left" / "right" / "left_up" / "right_up" while a run is on.
'
' Smoothness rules (the TV showed flicker otherwise):
'   - never change a Poster's uri during play: preload frames and toggle visibility (kitSprite)
'   - never create or remove nodes during play: reuse pooled nodes
'   - background layers scroll with Animation nodes (rendered every frame), not the timer

sub kitInit(id as string)
    m.gameId = id
    m.strips = []
    m.timer = m.top.createChild("Timer")
    m.timer.duration = 0.02
    m.timer.repeat = true
    m.timer.observeField("fire", "kitTick")
    m.clock = CreateObject("roTimespan")
    m.top.best = ReadBest(id)
end sub

sub kitTick()
    dt = m.clock.TotalMilliseconds() / 1000.0
    m.clock.Mark()
    if dt > 0.05 then dt = 0.05
    if m.top.state = "play" then advance(dt)
end sub

sub kitStart()
    m.top.newBest = false
    m.top.points = 0
    m.top.state = "play"
    m.clock.Mark()
    m.timer.control = "start"
    kitStrips("start")
end sub

sub kitOver()
    m.timer.control = "stop"
    kitStrips("pause")
    if m.top.points > m.top.best
        m.top.best = m.top.points
        m.top.newBest = true
        SaveBest(m.gameId, m.top.best)
    end if
    m.top.state = "over"
end sub

sub onKey()
    k = m.top.key
    st = m.top.state
    if k = "stop"
        m.timer.control = "stop"
        kitStrips("stop")
        reset()
        m.top.points = 0
        m.top.state = "ready"
    else if k = "pause"
        if st = "play"
            m.timer.control = "stop"
            kitStrips("pause")
            m.top.state = "paused"
        end if
    else if k = "OK"
        if st = "ready"
            kitStart()
            begin()
        else if st = "over"
            reset()
            kitStart()
            begin()
        else if st = "paused"
            m.clock.Mark()
            m.top.state = "play"
            m.timer.control = "start"
            kitStrips("resume")
        else
            action(k)
        end if
    else if st = "play"
        action(k)
    end if
end sub

' Preloaded animation frames: one Poster per frame inside a Group; switching only flips visibility.
function kitSprite(parent as object, uris as object, w as float, h as float) as object
    g = parent.createChild("Group")
    g.scaleRotateCenter = [w / 2, h / 2]
    frames = []
    for each u in uris
        p = g.createChild("Poster")
        p.uri = u
        p.width = w
        p.height = h
        p.visible = false
        frames.Push(p)
    end for
    frames[0].visible = true
    return { g: g, frames: frames, cur: 0 }
end function

sub kitFrame(sp as object, i as integer)
    if i = sp.cur then return
    sp.frames[sp.cur].visible = false
    sp.frames[i].visible = true
    sp.cur = i
end sub

' A seamless strip scrolled by the timer (for layers that must match the game speed).
function kitStrip(parent as object, uri as string, w as integer, h as integer, y as float) as object
    g = parent.createChild("Group")
    for i = 0 to 1
        p = g.createChild("Poster")
        p.uri = uri
        p.width = w
        p.height = h
        p.translation = [i * w, 0]
    end for
    s = { g: g, x: 0.0, w: w, y: y }
    kitScroll(s, 0)
    return s
end function

sub kitScroll(s as object, dx as float)
    s.x = s.x - dx
    while s.x <= -s.w
        s.x = s.x + s.w
    end while
    s.g.translation = [s.x, s.y]
end sub

' A background strip that scrolls on its own at a constant speed, every rendered frame.
sub kitStripAnim(parent as object, uri as string, w as integer, h as integer, y as float, speed as float)
    g = parent.createChild("Group")
    g.id = "strip" + m.strips.Count().ToStr()
    g.translation = [0, y]
    for i = 0 to 1
        p = g.createChild("Poster")
        p.uri = uri
        p.width = w
        p.height = h
        p.translation = [i * w, 0]
    end for
    a = m.top.createChild("Animation")
    a.repeat = true
    a.easeFunction = "linear"
    a.duration = w / speed
    v = a.createChild("Vector2DFieldInterpolator")
    v.fieldToInterp = g.id + ".translation"
    v.key = [0, 1]
    v.keyValue = [[0, y], [-w, y]]
    m.strips.Push({ g: g, anim: a, y: y })
end sub

sub kitStrips(control as string)
    for each s in m.strips
        s.anim.control = control
        if control = "stop" then s.g.translation = [0, s.y]
    end for
end sub

function kitHit(ax as float, ay as float, aw as float, ah as float, bx as float, by as float, bw as float, bh as float) as boolean
    return ax < bx + bw and ax + aw > bx and ay < by + bh and ay + ah > by
end function
