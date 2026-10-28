' Lumen constants. Every colour, theme, font and layout number lives here (spec 3, 4, 6).

function LumenTheme() as object
    return {
        yellow: "0xFFDE03FF"
        ink: "0x1C1C1CFF"
        ink60: "0x1C1C1C99"
        white: "0xFFFFFFFF"
        paper: "0xFBF7ECFF"

        ' Spec 3.2. Deep colours are opaque; opacity comes from the node using them.
        themes: {
            starry: { painting: "Starry Night", weather: "clear weather", deep: "0x14184AFF" }
            sunflower: { painting: "Sunflower Field", weather: "sunny weather", deep: "0x703E00FF" }
            irises: { painting: "Irises in the Rain", weather: "rain", deep: "0x2C2460FF" }
            blossom: { painting: "Almond Blossom", weather: "cloudy weather", deep: "0x0A464EFF" }
            snow: { painting: "Snowy Evening", weather: "snow", deep: "0x2E3068FF" }
        }

        ' Spec 3.3, in hash order.
        pastels: ["rose", "sky", "mint", "wheat", "lilac", "peach", "aqua", "cream"]

        ' Frosted controls: deep at 52% so white labels stay readable over busy paintings.
        frostOpacity: 0.52
        ' Focused controls: white liquid glass (tint opacity under the glass_*.9.png overlay).
        focusOpacity: 0.9

        ' Games panel: games use a 1210x342 logical stage, scaled to fit the panel's window.
        gameW: 1210
        gameH: 342

        ' Spec 6.5
        tileW: 288
        tileH: 192
        tilePitch: 324
        rowX: 96
        rowY: 624
        maxSlot: 3
    }
end function

' Spec 3.3: an app keeps its pastel forever. The phone uses the identical hash.
function PastelFor(appId as string) as string
    h = 0
    for i = 1 to Len(appId)
        h = (h * 31 + Asc(Mid(appId, i, 1))) mod 1000003
    end for
    return LumenTheme().pastels[h mod 8]
end function

function LumenFont(name as string, size as integer) as object
    f = CreateObject("roSGNode", "Font")
    f.uri = "pkg:/fonts/" + name + ".ttf"
    f.size = size
    return f
end function

' Converts an opaque colour to the same colour at the given opacity (0..1).
' Accepts "0xRRGGBBFF" strings and the integers that color fields return when read back.
function WithAlpha(color as dynamic, alpha as float) as string
    if type(color) = "roString" or type(color) = "String"
        rgb = Mid(color, 3, 6)
    else
        rgb = Hex2((color >> 24) and 255) + Hex2((color >> 16) and 255) + Hex2((color >> 8) and 255)
    end if
    return "0x" + UCase(rgb) + Hex2(Int(alpha * 255 + 0.5))
end function

function Hex2(n as integer) as string
    h = UCase(StrI(n, 16))
    if Len(h) < 2 then h = "0" + h
    return h
end function

' Best scores for the built-in games, kept in the registry.
function ReadBest(game as string) as integer
    v = CreateObject("roRegistrySection", "lumen").Read("best_" + game)
    if v = invalid or v = "" then return 0
    return v.ToInt()
end function

sub SaveBest(game as string, score as integer)
    sec = CreateObject("roRegistrySection", "lumen")
    sec.Write("best_" + game, score.ToStr())
    sec.Flush()
end sub

' Roku OS 16 refuses ECP from a channel to its own TV, so Lumen reaches ECP through the
' Lumen bridge (Mac helper now, phone app later), which announces itself via /input?bridge=.
function BridgeBase() as string
    url = CreateObject("roRegistrySection", "lumen").Read("bridge")
    if url = invalid then return ""
    return url
end function

sub SaveBridgeBase(url as string)
    sec = CreateObject("roRegistrySection", "lumen")
    sec.Write("bridge", url)
    sec.Flush()
end sub
