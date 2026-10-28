sub Main(args as dynamic)
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.setMessagePort(port)
    scene = screen.CreateScene("MainScene")
    screen.show()
    scene.launchArgs = args

    ' Spec 15.6: the phone talks to Lumen through POST /input while it's open.
    input = CreateObject("roInput")
    input.setMessagePort(port)

    while true
        msg = wait(0, port)
        t = type(msg)
        if t = "roSGScreenEvent"
            if msg.isScreenClosed() then return
        else if t = "roInputEvent"
            if msg.isInput() then scene.inputArgs = msg.getInfo()
        end if
    end while
end sub
