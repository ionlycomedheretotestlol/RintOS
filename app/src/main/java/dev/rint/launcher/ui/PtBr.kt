package dev.rint.launcher.ui

/** Brazilian Portuguese, keyed by the English text exactly as it appears in the code. */
internal object PtBr {
    val exact: Map<String, String> by lazy { HashMap<String, String>(1400).apply { core(); intro(); settings(); widgets(); enums() } }

    /** "{}" = kept as is; "{t}" on the right = kept and translated. */
    val patterns: List<Pair<String, String>> = listOf(
        // assistant
        "I need a {} API key first — add one in settings → Rin assistant." to "preciso de uma chave de API do {} primeiro — adicione uma em configurações → assistente Rin.",
        "hmm, that failed: {}" to "hmm, deu erro: {}",
        "stopping after {} steps." to "parando depois de {} passos.",
        "long-pressing element {}" to "segurando o elemento {}",
        "tapping element {}" to "tocando no elemento {}",
        "tapping ({}, {})" to "tocando em ({}, {})",
        "typing “{}”" to "digitando “{}”",
        "scrolling {}" to "rolando {t}",
        "pressing {}" to "apertando {t}",
        "opening {}" to "abrindo {}",
        "waiting {}s" to "esperando {}s",
        "finding “{}”" to "procurando “{}”",
        "hi, I'm {}. ask me anything" to "oi, eu sou o {}. pergunta qualquer coisa",
        "ask {}…" to "pergunte ao {}…",
        "ask {} anything…" to "pergunte qualquer coisa ao {}…",
        "ask {}: “{}”" to "perguntar ao {}: “{}”",
        "ask {}" to "pergunte ao {}",
        "{} says hi :3" to "{} diz oi :3",
        // home
        "play “{}”" to "tocar “{}”",
        "search the web for “{}”" to "pesquisar “{}” na web",
        "add {}×{}" to "adicionar {}×{}",
        "{}% · charging  ·  {} notifications" to "{}% · carregando  ·  {} notificações",
        "{}  ·  {} notifications" to "{}  ·  {} notificações",
        "{}% · charging" to "{}% · carregando",
        "{}% [charging]" to "{}% [carregando]",
        "{}% [discharging]" to "{}% [descarregando]",
        "saver mode · {}%" to "modo economia · {}%",
        // intro
        "every {}." to "cada {t}.",
        "  [ ok ] {} settings" to "  [ ok ] {} configurações",
        "{} settings" to "{} configurações",
        "tap around. this is just the start — there are {} more knobs inside." to "mexe à vontade. isso é só o começo — tem mais {} ajustes lá dentro.",
        // jokes
        "  deleted {}" to "  apagado {}",
        "{} GB of 16 GB · {}" to "{} GB de 16 GB · {}",
        // music
        "trying {}…" to "tentando {}…",
        "{} days ago" to "há {} dias",
        "lap {}" to "volta {}",
        "up {}d {}h" to "ligado há {}d {}h",
        "up {}h" to "ligado há {}h",
        "{} GB free" to "{} GB livres",
        "{}: not found" to "{}: não encontrado",
        "{}: couldn't play" to "{}: não deu pra tocar",
        "loading {}…" to "carregando {}…",
        "lyrics: {}" to "letra: {}",
        // settings
        "Rint settings · {}" to "Configurações do Rint · {t}",
        "nothing called “{}” — yet." to "nada chamado “{}” — ainda.",
        "{} settings · every one applies live" to "{} configurações · todas aplicam na hora",
        "SHORTCUT {}" to "ATALHO {}",
        // alerts
        "battery's at {}%" to "bateria em {}%",
        "only {}% left" to "só {}% restante",
        // widgets
        "♥ {}" to "♥ {t}",
        "H {}°  L {}°" to "Máx {}°  Mín {}°",
        "feels {}°" to "sensação {}°",
    )

    private fun HashMap<String, String>.core() {
        // assistant
        put("tell Rin something…", "fala algo pro Rin…")
        put("stop", "parar"); put("no", "não"); put("yes", "sim")
        put("thinking…", "pensando…")
        put("done — anything else?", "pronto — mais alguma coisa?")
        put("stopped.", "parado.")
        put("looking at the screen", "olhando a tela")
        put("typed text", "texto digitado")
        put("swiping", "deslizando")
        put("waiting for you", "esperando você")
        put("up", "pra cima"); put("down", "pra baixo"); put("left", "pra esquerda"); put("right", "pra direita")
        put("back", "voltar"); put("home", "início"); put("recents", "recentes"); put("notifications", "notificações")
        put("quick_settings", "configurações rápidas")
        put("I'm listening…", "tô ouvindo…")
        put("add an API key in Settings → Rin assistant and I'm all yours.", "adicione uma chave de API em Configurações → Assistente Rin e eu sou todo seu.")
        put("hey. what do you need?", "oi. do que você precisa?")
        put("type to Rin", "escreva pro Rin")
        put("listening · tap the mic to stop", "ouvindo · toque no microfone pra parar")
        put("close", "fechar")
        put("what's on my screen?", "o que tem na minha tela?")
        put("open YouTube and search lofi beats", "abre o YouTube e pesquisa lofi beats")
        put("make my launcher pink and bouncy", "deixa meu launcher rosa e saltitante")
        put("play something chill", "toca algo tranquilo")
        put("turn on the Terminal preset", "ativa o preset Terminal")
        put("tell me a fun fact", "me conta uma curiosidade")
        put("new chat", "nova conversa")
        put("listening…", "ouvindo…")
        put("TRY", "TENTE")
        put("yes, do it", "sim, pode fazer")
        put("give me a brain first", "me dá um cérebro primeiro")
        put("Pick Gemini, Groq, Claude or OpenRouter and paste an API key. A free Gemini key also gives me a voice.",
            "Escolha Gemini, Groq, Claude ou OpenRouter e cole uma chave de API. Uma chave grátis do Gemini também me dá uma voz.")
        put("open settings", "abrir configurações")
        put("settings", "configurações"); put("talking", "falando"); put("send", "enviar")
        put("hi! I'm Rin. this is my voice. pretty cool, right?", "oi! eu sou o Rin. essa é a minha voz. bem legal, né?")
        // crash report
        put("oops — something broke", "ops — algo quebrou")
        put("Copy or share this report so it can be fixed.", "Copie ou compartilhe este relatório pra que dê pra consertar.")
        put("RintOS crash", "Erro no RintOS")
        put("Share crash report", "Compartilhar relatório de erro")
        put("dismiss", "dispensar"); put("copy", "copiar"); put("copied", "copiado"); put("share", "compartilhar")
        // drawer / home
        put("RECENT", "RECENTES")
        put("jump to settings", "ir para as configurações")
        put("with live lyrics in Rint Music", "com letra ao vivo no Rint Music")
        put("your AI assistant can answer or do it for you", "seu assistente de IA pode responder ou fazer por você")
        put("contact", "contato")
        put("layout is locked (settings → home)", "layout travado (configurações → tela inicial)")
        put("turn on RintOS gestures in Accessibility to lock with a gesture", "ative os gestos do RintOS em Acessibilidade pra bloquear com um gesto")
        put("needs RintOS gestures (Accessibility)", "precisa dos gestos do RintOS (Acessibilidade)")
        put("flashlight on", "lanterna ligada"); put("flashlight off", "lanterna desligada")
        put("pick an app for this gesture in settings", "escolha um app pra esse gesto nas configurações")
        put("widget added", "widget adicionado")
        put("no room on this page", "sem espaço nesta página")
        put("added to home", "adicionado à tela inicial")
        put("Widgets", "Widgets"); put("Wallpaper", "Papel de parede"); put("Customize look", "Personalizar visual")
        put("RintOS settings", "Configurações do RintOS"); put("Add a page", "Adicionar página")
        put("Unlock layout", "Destravar layout"); put("Lock layout", "Travar layout"); put("Rename", "Renomear")
        put("Add to home", "Adicionar à tela inicial"); put("Add to dock", "Adicionar ao dock")
        put("Remove from dock", "Remover do dock"); put("Remove from home", "Remover da tela inicial")
        put("Unhide app", "Mostrar app"); put("Hide from drawer", "Ocultar da gaveta")
        put("back in the drawer", "de volta na gaveta"); put("hidden — search still finds it", "oculto — a busca ainda encontra")
        put("App info", "Informações do app"); put("Uninstall", "Desinstalar"); put("Widget", "Widget"); put("SIZE", "TAMANHO")
        put("not enough room — move things first", "sem espaço — mova as coisas primeiro")
        put("clock styles →", "estilos de relógio →"); put("Remove widget", "Remover widget"); put("Apps", "Apps")
        put("reset", "redefinir"); put("save", "salvar")
        put("tap the dot", "toque no ponto")
        put("saver mode. ask me anything, I'll keep it light.", "modo economia. pergunta qualquer coisa, vou pegar leve.")
        put("ask Rin…", "pergunte ao Rin…"); put("search apps", "buscar apps")
        // lock
        put("· charging", "· carregando")
        put("nothing playing", "nada tocando")
        put("play something from the music widget", "toque algo pelo widget de música")
        put("swipe up to unlock", "deslize pra cima pra desbloquear")
        // mascot
        put("morning!", "bom dia!"); put("hey hey", "oi oi"); put("evening~", "boa noite~"); put("up late?", "acordado até tarde?")
        put("yum, power", "nham, energia"); put("hi!", "oi!"); put("boop", "bup")
        put("you pressed it.", "você apertou."); put("…oh. an audience.", "…ah. uma plateia.")
        put("thank you, thank you", "obrigado, obrigado"); put("GUITAR SOLO", "SOLO DE GUITARRA")
        put("tap anywhere to leave the concert", "toque em qualquer lugar pra sair do show")
        put("Delete System32", "Apagar a System32"); put("what could go wrong", "o que pode dar errado")
        put("Download more RAM", "Baixar mais RAM"); put("16 GB, free, totally legit", "16 GB, grátis, super confiável")
        put("Make Rin sneeze", "Fazer o Rin espirrar"); put("bless him in advance", "saúde, desde já")
        put("Summon 100 Rins", "Invocar 100 Rins"); put("there is no undo", "não tem como desfazer")
        put("Flip the phone upside down", "Virar o celular de cabeça pra baixo"); put("the software way", "do jeito software")
        put("Spin to win", "Gire pra ganhar"); put("you won't win", "você não vai ganhar")
        put("Make Rin do a backflip", "Fazer o Rin dar um mortal"); put("he's been practicing", "ele andou treinando")
        put("Self destruct", "Autodestruição")
        put("wrong way up!", "de cabeça pra baixo!"); put("wheeeee", "uhuuuul")
        put("the recycle bin", "a lixeira"); put("the start button", "o botão iniciar"); put("your homework", "sua lição de casa")
        put("Deleting C:\\Windows\\System32", "Apagando C:\\Windows\\System32")
        put("just kidding. this is Android. there is no System32.", "brincadeira. isso é Android. não existe System32.")
        put("Downloading 16 GB of RAM…", "Baixando 16 GB de RAM…")
        put("…I ate it. it was crunchy.", "…eu comi. tava crocante.")
        put("RAM added: 0 bytes", "RAM adicionada: 0 bytes")
        put("CHOO!!", "TCHIM!!"); put("…excuse me.", "…com licença."); put("a-a-a…", "a-a-a…")
        put("we live here now.", "agora a gente mora aqui."); put("10/10. nailed it.", "10/10. mandou bem.")
        put("SELF DESTRUCT", "AUTODESTRUIÇÃO"); put("…you really pressed it. twice.", "…você apertou mesmo. duas vezes.")
        put("nothing was destroyed. except your trust in buttons.", "nada foi destruído. só sua confiança em botões.")
        // music
        put("finding a source…", "procurando uma fonte…")
        put("Audius (full song)", "Audius (música completa)")
        put("Audius: couldn't play", "Audius: não deu pra tocar")
        put("Audius (closest match)", "Audius (mais parecida)")
        put("no free full version of this one. tap → open it in your music app.", "essa não tem versão completa grátis. toque → abrir no seu app de música.")
        put("this phone", "este celular"); put("FULL", "COMPLETA"); put("full song · Audius", "música completa · Audius")
        put("search a song", "busque uma música")
        put("type a song — it plays right here,\nlyrics and all.", "digite uma música — ela toca aqui mesmo,\ncom letra e tudo.")
        put("searching…", "buscando…"); put("nothing found", "nada encontrado")
        put("· full song", "· música completa"); put("· 30s preview", "· prévia de 30s")
        put("loading…", "carregando…")
        put("only a preview was free for this one. tap → full song in your music app", "só tinha prévia grátis dessa. toque → música completa no seu app de música")
        put("finding lyrics…", "procurando a letra…")
        put("Go to fullscreen", "Tela cheia"); put("Pause", "Pausar"); put("Play", "Tocar")
        put("Open the full song in my music app", "Abrir a música completa no meu app de música")
        put("Stop music", "Parar música"); put("stop music", "parar música")
        put("on this phone", "neste celular"); put("your music app", "seu app de música")
        put("pick a song, any song", "escolha uma música, qualquer uma")
        put("waiting for it to start playing", "esperando começar a tocar")
        put("no lyrics for this one.\njust vibe.", "sem letra pra essa.\nsó curte.")
        put("unsynced lyrics", "letra sem sincronia"); put("song, artist, vibe…", "música, artista, vibe…")
        put("on device", "no aparelho"); put("play it with…", "tocar com…")
        put("RintOS starts the song in your music app and follows along with lyrics.", "O RintOS começa a música no seu app de música e acompanha com a letra.")
        put("streaming", "transmitindo"); put("search", "buscar"); put("sync", "sincronia")
        // alerts
        put("tap to dismiss", "toque pra dispensar"); put("Emergency alert", "Alerta de emergência")
        put("I folded your home screen into saver mode so it lasts longer. It comes back when you charge.",
            "Dobrei sua tela inicial no modo economia pra bateria durar mais. Ela volta quando você carregar.")
        put("1% left!!", "1% restante!!")
        put("your phone is about to turn off. plug it in now.", "seu celular vai desligar. coloque pra carregar agora.")
        put("find a charger soon — I'm keeping things light.", "ache um carregador logo — tô pegando leve.")
    }

    private fun HashMap<String, String>.intro() {
        put("signal detected", "sinal detectado"); put("it's waking up.", "está acordando.")
        put("> rintos --the-story-so-far", "> rintos --a-historia-ate-aqui")
        put("  names: FishinOS, QuotOS, ThatOS, TrulyOS…", "  nomes: FishinOS, QuotOS, ThatOS, TrulyOS…")
        put("  [ ok ] settled on: RintOS", "  [ ok ] escolhido: RintOS")
        put("  [ !! ] build 1: crashed", "  [ !! ] build 1: travou")
        put("  [ !! ] build 2: crashed harder", "  [ !! ] build 2: travou mais ainda")
        put("  [ ok ] Rin: 48 pixels → a whole cat", "  [ ok ] Rin: 48 pixels → um gato")
        put("  [ !! ] music widget: \"suffering\"", "  [ !! ] widget música: \"sofrendo\"")
        put("  [ ok ] music widget: fixed", "  [ ok ] widget música: consertado")
        put("  [ ok ] v1.0 shipped", "  [ ok ] v1.0 lançada")
        put("  [ !! ] saver mode: worked once", "  [ !! ] economia: funcionou 1 vez")
        put("  [ ok ] saver mode: works every time", "  [ ok ] economia: funciona sempre")
        put("  [ ok ] no YouTube. real music.", "  [ ok ] sem YouTube. música real.")
        put("  [ ok ] tested. and tested. and tested.", "  [ ok ] testado. e testado. e mais.")
        put("  compiling 1.3…", "  compilando 1.3…"); put("  ready.", "  pronto.")
        put("version 1.3 — the real one.", "versão 1.3 — a de verdade.")
        put("SHAPE", "FORMA"); put("COLOR", "COR"); put("CLOCK", "RELÓGIO"); put("PIXEL", "PIXEL")
        put("shape", "forma"); put("color", "cor"); put("clock", "relógio"); put("pixel", "pixel")
        put("your phone, your rules ♪", "seu celular, suas regras ♪")
        put("it's finally here.", "finalmente chegou.")
        put("RintOS 1.3. the real one.", "RintOS 1.3. o de verdade.")
        put("you made this happen.", "você fez isso acontecer.")
        put("let's make it yours →", "vamos deixar do seu jeito →")
        put("survived the crash era", "sobreviveu à era dos crashes"); put("him.", "ele.")
        put("the evolution of Rin", "a evolução do Rin"); put("look how far he's come.", "olha o quanto ele cresceu.")
        put("faster.", "mais rápido."); put("every widget, alive", "cada widget, vivo")
        put("a notch that's yours", "um notch só seu"); put("10 lock screens", "10 telas de bloqueio")
        put("photos, your wallpapers", "fotos, seus papéis de parede"); put("custom colors. all of them", "cores personalizadas. todas")
        put("gestures for everything", "gestos pra tudo"); put("…and it's free", "…e é grátis")
        put("Rin: hold home, just talk", "Rin: segure o início e fale"); put("songs play right in the widget", "músicas tocam direto no widget")
        put("battery saver dot mode", "modo ponto de economia"); put("serious alerts only", "só alertas sérios")
        put("no ads. no tracking.", "sem anúncios. sem rastreio."); put("made by one person", "feito por uma pessoa")
        put("…one more thing", "…mais uma coisa")
        put("INTRODUCING", "APRESENTANDO"); put("the real one.", "o de verdade.")
        put("SECOND DROP", "SEGUNDO DROP"); put("warp speed →", "velocidade da luz →")
        put("you tested it.", "você testou."); put("again.", "de novo."); put("and again.", "e de novo.")
        put("and it got better.", "e ficou melhor."); put("wait.", "espera.")
        put("made by Carrot", "feito por Carrot"); put("starring Rin", "estrelando Rin")
        put("music: synthesized live · 0 audio files", "música: sintetizada ao vivo · 0 arquivos de áudio")
        put("3D: hand-built renderer · no WebView", "3D: renderizador feito à mão · sem WebView")
        put("thanks for waiting.", "obrigado por esperar.")
        put("icons", "ícones"); put("accent", "destaque"); put("dock", "dock"); put("notch", "notch")
        put("wallpaper", "papel de parede"); put("style", "estilo")
        put("Rin, your AI", "Rin, sua IA"); put("songs play right here", "músicas tocam aqui mesmo")
        put("your photo & live wallpapers", "sua foto e papéis animados"); put("battery saver dot", "ponto de economia")
        // welcome guide
        put("hey! welcome home.\nwant the 30-second tour?", "oi! bem-vindo ao lar.\nquer um tour de 30 segundos?")
        put("swipe up anywhere → every app,\nplus search that does math & music.", "deslize pra cima em qualquer lugar → todos os apps,\ne uma busca que faz contas e toca música.")
        put("hold on empty space → widgets,\nwallpaper and the big settings.", "segure num espaço vazio → widgets,\npapel de parede e as configurações.")
        put("hold any app → rename it, hide it,\nor drag it wherever you want.", "segure qualquer app → renomeie, oculte\nou arraste pra onde quiser.")
        put("see the notch up top? tap it.\nhold it for music.", "tá vendo o notch lá em cima? toque nele.\nsegure pra música.")
        put("the music widget plays any song\nwith live lyrics. I dance in the breaks.", "o widget de música toca qualquer música\ncom letra ao vivo. eu danço nos intervalos.")
        put("double-tap empty space to lock\n(needs one permission).", "toque duas vezes num espaço vazio pra bloquear\n(precisa de uma permissão).")
        put("and literally everything is customizable.\nhave fun. I'll be around :3", "e literalmente tudo é personalizável.\ndivirta-se. tô por aqui :3")
        put("sure!", "claro!"); put("let's go", "bora"); put("skip", "pular"); put("next", "próximo")
        // setup flow
        put("waking up pixels", "acordando os pixels"); put("finding your apps", "achando seus apps")
        put("polishing icons", "polindo os ícones"); put("teaching Rin your name", "ensinando seu nome pro Rin")
        put("building your home", "montando sua tela inicial"); put("placing widgets", "posicionando widgets")
        put("fluffing the dock", "afofando o dock"); put("done!", "pronto!"); put("skip ›", "pular ›")
        put("first, make it yours.", "primeiro, deixe do seu jeito."); put("icon look", "visual dos ícones")
        put("rint white", "rint branco"); put("rint black", "rint preto"); put("looks good →", "ficou bom →")
        put("START", "COMEÇAR"); put("tap START", "toque em COMEÇAR")
        put("Be your home screen", "Ser sua tela inicial"); put("so pressing home opens RintOS", "pra que o botão início abra o RintOS")
        put("Notification access", "Acesso às notificações")
        put("notification dots, and live lyrics for Spotify, YT Music & friends", "pontos de notificação e letras ao vivo pro Spotify, YT Music e cia")
        put("Music on this phone", "Músicas neste celular"); put("play your own song files with lyrics", "toque seus próprios arquivos de música com letra")
        put("Contacts", "Contatos"); put("find people right from search", "ache pessoas direto na busca")
        put("Gesture helper", "Ajudante de gestos")
        put("double-tap to lock & recents gestures (accessibility). optional.", "tocar duas vezes pra bloquear e gestos de recentes (acessibilidade). opcional.")
        put("a few permissions", "algumas permissões")
        put("each one unlocks something. skip any — you can grant them later in settings.", "cada uma desbloqueia algo. pule qualquer uma — dá pra permitir depois nas configurações.")
        put("recommended", "recomendado"); put("continue →", "continuar →"); put("allow", "permitir"); put("on", "ativado")
        put("now playing", "tocando agora"); put("♪ your song here", "♪ sua música aqui")
    }

    private fun HashMap<String, String>.settings() {
        put("Settings", "Configurações"); put("Presets", "Presets"); put("Look", "Visual"); put("Features", "Recursos")
        put("Behavior", "Comportamento"); put("System", "Sistema"); put("search settings…", "buscar configurações…"); put("LIVE", "AO VIVO")
        put("wallpaper set", "papel de parede definido"); put("couldn't open that photo", "não deu pra abrir essa foto")
        put("pick app", "escolher app"); put("+ add", "+ adicionar")
        put("fresh look applied", "visual novo aplicado"); put("My RintOS setup", "Meu setup do RintOS"); put("Export RintOS setup", "Exportar setup do RintOS")
        put("Icon pack", "Pacote de ícones"); put("None — use RintOS styles", "Nenhum — usar estilos do RintOS")
        put("No icon packs installed. Grab any ADW/Nova-compatible pack from the Play Store.", "Nenhum pacote de ícones instalado. Baixe qualquer pacote compatível com ADW/Nova na Play Store.")
        put("Nothing hidden. Long-press any app → Hide from drawer.", "Nada oculto. Segure qualquer app → Ocultar da gaveta.")
        put("RintOS asks this app to play what you search, then follows along with live lyrics.", "O RintOS pede pra esse app tocar o que você buscar e acompanha com a letra ao vivo.")
        put("No compatible music apps found. Spotify, YouTube Music, Deezer and most players work.", "Nenhum app de música compatível encontrado. Spotify, YouTube Music, Deezer e a maioria dos players funcionam.")
        put("setup imported ✓", "setup importado ✓"); put("that doesn't look like a RintOS setup", "isso não parece um setup do RintOS")
        put("Reset everything?", "Redefinir tudo?")
        put("Your layout, widgets and every setting go back to day one. Rin will forget you (he'll get over it).", "Seu layout, widgets e todas as configurações voltam ao primeiro dia. O Rin vai esquecer você (ele supera).")
        put("remove", "remover"); put("change", "trocar"); put("unhide", "mostrar"); put("cancel", "cancelar")
        put("hue", "matiz"); put("saturation", "saturação"); put("brightness", "brilho"); put("opacity", "opacidade")
        // presets
        put("white, blue, black. the classic.", "branco, azul, preto. o clássico.")
        put("green phosphor, blocky everything", "fósforo verde, tudo em blocos"); put("run…", "executar…")
        put("Paper", "Papel"); put("light, calm, ink icons", "claro, calmo, ícones de tinta")
        put("Candy", "Doce"); put("pink, round, extremely bouncy", "rosa, redondo, extremamente saltitante")
        put("Glass", "Vidro"); put("see-through, floaty, moving colors", "transparente, flutuante, cores em movimento")
        put("Minimal", "Mínimo"); put("no labels, no dock, just calm", "sem nomes, sem dock, só calma")
        put("Retro", "Retrô"); put("pixel fonts, hexes, orange CRT", "fontes pixel, hexágonos, CRT laranja")
        // schema
        put("Language", "Idioma"); put("the whole app, and how Rin talks", "o app inteiro, e como o Rin fala")
        put("Look & feel", "Aparência"); put("theme, accent, wallpaper, fonts", "tema, destaque, papel de parede, fontes")
        put("Theme", "Tema"); put("Accent color", "Cor de destaque"); put("used everywhere: buttons, highlights, lyrics", "usada em tudo: botões, destaques, letras")
        put("Interface font", "Fonte da interface"); put("Corner roundness", "Arredondamento dos cantos"); put("Panel opacity", "Opacidade dos painéis")
        put("how see-through menus and widgets are", "o quanto menus e widgets são transparentes")
        put("Background blur", "Desfoque do fundo"); put("blur behind drawer & overlays (Android 12+)", "desfoque atrás da gaveta e sobreposições (Android 12+)")
        put("Label shadows", "Sombra nos nomes"); put("Show status bar", "Mostrar barra de status"); put("Show navigation bar", "Mostrar barra de navegação")
        put("Your own colors", "Suas próprias cores"); put("Custom colors", "Cores personalizadas")
        put("override the theme's background, panels and text", "substitui o fundo, os painéis e o texto do tema")
        put("Background", "Fundo"); put("Panels & widgets", "Painéis e widgets"); put("Text", "Texto"); put("Secondary text", "Texto secundário")
        put("Film grain", "Granulado de filme"); put("a subtle analog texture over the wallpaper", "uma textura analógica sutil sobre o papel de parede")
        put("Aurora third color", "Terceira cor da aurora"); put("system, or one RintOS paints for you", "do sistema, ou um que o RintOS pinta pra você")
        put("Artwork", "Arte"); put("RintOS's own wallpapers (used when Wallpaper = art)", "papéis de parede do próprio RintOS (usados quando Papel de parede = arte)")
        put("Use my own photo", "Usar minha própria foto"); put("pick any picture from your gallery", "escolha qualquer imagem da sua galeria")
        put("Choose a live wallpaper", "Escolher papel de parede animado"); put("any live wallpaper installed on your phone", "qualquer papel de parede animado instalado no celular")
        put("Pick system wallpaper", "Escolher papel de parede do sistema"); put("Solid color", "Cor sólida")
        put("Gradient start", "Início do gradiente"); put("Gradient end", "Fim do gradiente"); put("Gradient angle", "Ângulo do gradiente")
        put("Wallpaper dim", "Escurecer papel de parede")
        put("Icons", "Ícones"); put("shapes, styles, packs, badges", "formatos, estilos, pacotes, selos")
        put("Icon style", "Estilo dos ícones"); put("Rint = the white/blue signature look", "Rint = o visual branco/azul característico")
        put("Shape", "Formato"); put("Rint background", "Fundo Rint"); put("behind the glyph in Rint style", "atrás do símbolo no estilo Rint")
        put("Glyph color", "Cor do símbolo"); put("Icon size", "Tamanho dos ícones"); put("Inner padding", "Margem interna")
        put("Drop shadow", "Sombra"); put("Outline width", "Espessura do contorno"); put("Outline color", "Cor do contorno")
        put("Press effect", "Efeito ao tocar"); put("Notification dots", "Pontos de notificação"); put("needs notification access", "precisa de acesso às notificações")
        put("Dot color", "Cor do ponto"); put("Icon packs", "Pacotes de ícones"); put("Choose icon pack", "Escolher pacote de ícones")
        put("any ADW / Nova compatible pack", "qualquer pacote compatível com ADW / Nova"); put("Shape pack icons too", "Aplicar formato nos ícones do pacote")
        put("Labels", "Nomes"); put("app names under icons", "nomes dos apps embaixo dos ícones"); put("Show labels", "Mostrar nomes")
        put("Text size", "Tamanho do texto"); put("Color", "Cor"); put("Max lines", "Máximo de linhas"); put("UPPERCASE", "MAIÚSCULAS"); put("Bold", "Negrito")
        put("Home screen", "Tela inicial"); put("grid, pages, transitions", "grade, páginas, transições")
        put("Columns", "Colunas"); put("Rows", "Linhas"); put("Page transition", "Transição de página"); put("Page indicator", "Indicador de página")
        put("Side margin", "Margem lateral"); put("Top margin", "Margem superior"); put("stops accidental drags", "evita arrastar sem querer")
        put("Search bar", "Barra de busca"); put("Position", "Posição"); put("Style", "Estilo"); put("Placeholder text", "Texto de exemplo")
        put("Dock", "Dock"); put("glass, magnify, count", "vidro, ampliação, quantidade"); put("Show dock", "Mostrar dock")
        put("Magnify on touch", "Ampliar ao tocar"); put("macOS-style wave when you slide across", "onda estilo macOS quando você desliza")
        put("Height", "Altura"); put("Corner", "Canto"); put("Background opacity", "Opacidade do fundo"); put("Icon scale", "Escala dos ícones")
        put("Labels in dock", "Nomes no dock")
        put("App drawer", "Gaveta de apps"); put("layout, sorting, hidden apps", "layout, ordem, apps ocultos")
        put("Layout", "Layout"); put("Sort by", "Ordenar por"); put("Open keyboard automatically", "Abrir teclado automaticamente")
        put("Recent apps row", "Fileira de apps recentes"); put("Letter headers", "Cabeçalhos com letras"); put("Hidden apps", "Apps ocultos"); put("see and unhide", "ver e mostrar")
        put("Search", "Busca"); put("engine, calculator, shortcuts", "buscador, calculadora, atalhos")
        put("Web engine", "Buscador da web"); put("Custom engine URL", "URL de buscador personalizado")
        put("Calculator", "Calculadora"); put("type 12*4+2 and get the answer", "digite 12*4+2 e veja a resposta")
        put("Unit converter", "Conversor de unidades"); put("“5 km to mi”, “70 f to c”", "“5 km to mi”, “70 f to c”")
        put("Settings shortcuts", "Atalhos de configurações"); put("Fuzzy matching", "Busca aproximada"); put("“yt” finds YouTube", "“yt” encontra o YouTube")
        put("Enter launches top app", "Enter abre o primeiro app"); put("Web search fallback", "Buscar na web se nada achar")
        put("Gestures", "Gestos"); put("swipes, taps, shortcuts", "deslizes, toques, atalhos")
        put("Swipe up", "Deslizar pra cima"); put("Swipe down", "Deslizar pra baixo"); put("Double tap", "Toque duplo")
        put("Two-finger swipe down", "Deslizar com dois dedos pra baixo"); put("Home button (on home)", "Botão início (na tela inicial)")
        put("Swipe up on dock", "Deslizar pra cima no dock"); put("Long-press notch", "Segurar o notch"); put("Haptics", "Vibração")
        put("Enable lock & recents gestures", "Ativar gestos de bloquear e recentes"); put("turns on the RintOS accessibility service", "ativa o serviço de acessibilidade do RintOS")
        put("Notch", "Notch"); put("your own dynamic island", "sua própria ilha dinâmica"); put("Show notch", "Mostrar notch")
        put("Width", "Largura"); put("Distance from top", "Distância do topo"); put("Accent glow", "Brilho de destaque")
        put("Left side", "Lado esquerdo"); put("Right side", "Lado direito"); put("Live activity", "Atividade ao vivo")
        put("grows with artwork + equalizer while music plays", "cresce com capa + equalizador enquanto a música toca"); put("Tap to expand", "Tocar pra expandir")
        put("Open & close sounds", "Sons ao abrir e fechar"); put("quiet while music plays or the phone is on silent", "fica quieto com música tocando ou no silencioso")
        put("Show in every app", "Mostrar em todos os apps"); put("the notch floats over other apps too (needs \"display over other apps\")", "o notch flutua sobre outros apps também (precisa de \"sobrepor outros apps\")")
        put("Clock", "Relógio"); put("tty blocks & 6 more faces", "blocos tty e mais 6 estilos"); put("Face", "Estilo")
        put("Seconds", "Segundos"); put("Blinking colon", "Dois-pontos piscando"); put("Show date", "Mostrar data"); put("Date format", "Formato da data")
        put("24-hour", "24 horas"); put("Use accent color", "Usar cor de destaque"); put("Size", "Tamanho")
        put("Greeting under the clock", "Saudação embaixo do relógio"); put("Alignment", "Alinhamento")
        put("Rin", "Rin"); put("your little roommate", "seu colega de quarto"); put("Rin lives here", "O Rin mora aqui")
        put("Art style", "Estilo de arte"); put("smooth vector art, or the original pixel look", "arte vetorial suave, ou o visual pixel original")
        put("Ear, tail & zipper color", "Cor da orelha, rabo e zíper"); put("follows your accent until you pick one here", "segue sua cor de destaque até você escolher uma aqui")
        put("Name", "Nome"); put("How often he shows up", "Com que frequência ele aparece"); put("Says hi each day", "Diz oi todo dia")
        put("Sleeps at night", "Dorme à noite"); put("Reacts to charging", "Reage ao carregar"); put("Head-bobs in music breaks", "Balança a cabeça nos intervalos da música")
        put("Rint Music", "Rint Music"); put("lyrics look & playback", "visual da letra e reprodução")
        put("Lyrics font", "Fonte da letra"); put("Lyrics size", "Tamanho da letra"); put("Upcoming lines", "Próximas linhas")
        put("Karaoke highlight", "Destaque karaokê"); put("Background dim", "Escurecer fundo"); put("Slow artwork drift", "Capa se movendo devagar")
        put("Play songs with", "Tocar músicas com"); put("right here = full songs streamed from Audius", "aqui mesmo = músicas completas pelo Audius")
        put("ask each time", "perguntar toda vez"); put("my music app", "meu app de música"); put("files on phone", "arquivos no celular")
        put("right here (free music APIs)", "aqui mesmo (APIs de música grátis)")
        put("Choose music app", "Escolher app de música"); put("Lyrics on the widget", "Letra no widget")
        put("Allow display over other apps", "Permitir sobrepor outros apps"); put("Lyrics offset", "Ajuste da letra"); put("nudge if lyrics run early/late", "ajuste se a letra vier adiantada/atrasada")
        put("Lock screen", "Tela de bloqueio"); put("10 styles, 10 shortcuts, unlock effects", "10 estilos, 10 atalhos, efeitos de desbloqueio")
        put("RintOS lock screen", "Tela de bloqueio do RintOS")
        put("shows over the system lock screen (your PIN/fingerprint still protects the phone). needs the RintOS gesture helper in Accessibility.",
            "aparece sobre a tela de bloqueio do sistema (seu PIN/digital ainda protege o celular). precisa do ajudante de gestos do RintOS em Acessibilidade.")
        put("Preview it", "Ver prévia"); put("Shortcuts", "Atalhos"); put("up to 10. camera & flashlight work without unlocking", "até 10. câmera e lanterna funcionam sem desbloquear")
        put("Unlock effect", "Efeito de desbloqueio"); put("Notification icons", "Ícones de notificação"); put("Music controls", "Controles de música")
        put("Rin keeps you company", "O Rin te faz companhia"); put("Battery", "Bateria"); put("Accent-colored clock", "Relógio na cor de destaque")
        put("Message", "Mensagem"); put("e.g. “if found, call 555-0100”", "ex.: “se achar, ligue 555-0100”")
        put("Rin assistant", "Assistente Rin"); put("AI that talks, sees & taps for you", "IA que fala, vê e toca por você")
        put("Brain", "Cérebro"); put("which AI powers Rin", "qual IA move o Rin"); put("API keys (stored only on this phone)", "Chaves de API (guardadas só neste celular)")
        put("Gemini API key", "Chave de API do Gemini")
        put("free at aistudio.google.com (new AQ. keys work) — also unlocks Rin's voice", "grátis em aistudio.google.com (as novas chaves AQ. funcionam) — também libera a voz do Rin")
        put("Groq API key", "Chave de API do Groq"); put("Claude API key", "Chave de API do Claude"); put("OpenRouter API key", "Chave de API do OpenRouter")
        put("Models (type any model name)", "Modelos (digite qualquer nome de modelo)")
        put("Gemini model", "Modelo do Gemini"); put("Groq model", "Modelo do Groq"); put("pick a vision model so Rin can see screenshots", "escolha um modelo com visão pro Rin ver capturas de tela")
        put("Claude model", "Modelo do Claude"); put("OpenRouter model", "Modelo do OpenRouter")
        put("Voice", "Voz"); put("Rin talks out loud", "O Rin fala em voz alta"); put("needs a Gemini API key (free tier)", "precisa de uma chave de API do Gemini (plano grátis)")
        put("Voice engine", "Motor de voz"); put("Android's built-in voice is the offline fallback", "a voz nativa do Android é a alternativa offline")
        put("Gemini voice", "Voz do Gemini"); put("Kore, Puck, Leda, Zephyr, Aoede, Charon…", "Kore, Puck, Leda, Zephyr, Aoede, Charon…")
        put("Gemini speech model", "Modelo de fala do Gemini"); put("Hands-free conversation", "Conversa sem as mãos")
        put("Rin listens again after answering", "O Rin volta a ouvir depois de responder"); put("Test Rin's voice", "Testar a voz do Rin")
        put("Phone control", "Controle do celular"); put("Let Rin operate the phone", "Deixar o Rin operar o celular")
        put("screenshots, taps, typing, swipes — only while doing a task you asked for", "capturas de tela, toques, digitação, deslizes — só durante uma tarefa que você pediu")
        put("Ask before risky steps", "Perguntar antes de passos arriscados"); put("sending, buying, deleting, posting", "enviar, comprar, apagar, postar")
        put("Max steps per task", "Máximo de passos por tarefa"); put("Extra personality", "Personalidade extra"); put("e.g. “answer like a pirate”", "ex.: “responda como um pirata”")
        put("Turn on phone control (Accessibility)", "Ativar controle do celular (Acessibilidade)")
        put("Make Rin your phone's assistant", "Tornar o Rin o assistente do celular"); put("then hold the home button anywhere to talk to him", "aí é só segurar o botão início em qualquer lugar pra falar com ele")
        put("Motion", "Movimento"); put("speed, bounce, app opening", "velocidade, elasticidade, abertura de apps")
        put("Animation speed", "Velocidade das animações"); put("Bounciness", "Elasticidade"); put("lower = more jelly", "menor = mais gelatina")
        put("App open animation", "Animação ao abrir app"); put("Wallpaper parallax", "Paralaxe do papel de parede"); put("Reduce motion", "Reduzir movimento")
        put("Permissions", "Permissões"); put("default launcher & access", "launcher padrão e acessos")
        put("Set RintOS as default home", "Definir RintOS como tela inicial padrão"); put("dots + live lyrics from any music app", "pontos + letras ao vivo de qualquer app de música")
        put("Accessibility (lock / recents gestures)", "Acessibilidade (gestos de bloquear / recentes)")
        put("Replay the welcome guide", "Rever o guia de boas-vindas"); put("Replay the intro", "Rever a intro")
        put("Battery & alerts", "Bateria e alertas"); put("saver mode, serious warnings only", "modo economia, só avisos sérios")
        put("Battery saver mode", "Modo economia de bateria")
        put("when battery gets low, home folds into a single dot: no widgets, almost no animation", "quando a bateria fica baixa, a tela inicial vira um único ponto: sem widgets, quase sem animação")
        put("Turn on at", "Ativar em"); put("Rin warns about low battery", "O Rin avisa quando a bateria está baixa"); put("at saver mode, 5% and 1%", "no modo economia, em 5% e em 1%")
        put("Emergency alerts", "Alertas de emergência")
        put("Rin pops up for tornado, amber and other emergency broadcasts (needs notification access)", "o Rin aparece para tornados, alertas amber e outras emergências (precisa de acesso às notificações)")
        put("Allow Rin's popups over other apps", "Permitir popups do Rin sobre outros apps"); put("Try saver mode now", "Testar o modo economia agora")
        put("Saver Home", "Início Econômico"); put("a lighter home: no widgets or wandering Rin, calmer wallpaper. Good for weak phones, not for daily use", "uma tela inicial mais leve: sem widgets nem Rin passeando, papel de parede mais calmo. Boa pra celulares fracos, não pro dia a dia")
        put("making home lighter…", "deixando a tela inicial mais leve…"); put("packing away widgets", "guardando os widgets")
        put("Dangerous", "Perigoso"); put("do not press. seriously.", "não aperte. sério.")
        put("Watch Rin play the guitar", "Ver o Rin tocar guitarra"); put("you have been warned", "você foi avisado")
        put("Backup & reset", "Backup e redefinir"); put("export, import, start over", "exportar, importar, recomeçar")
        put("Export setup", "Exportar setup"); put("copies your whole config + layout", "copia toda sua configuração + layout")
        put("Import setup", "Importar setup"); put("paste an exported setup", "cole um setup exportado")
        put("Reset look to defaults", "Redefinir visual para o padrão"); put("Reset everything", "Redefinir tudo")
        put("Rin button next to search", "Botão do Rin ao lado da busca"); put("one tap to talk to Rin", "um toque pra falar com o Rin")
        put("Startup screen", "Tela de inicialização"); put("what you see right after the phone boots", "o que você vê logo depois que o celular liga")
        put("Show after every restart", "Mostrar depois de cada reinício"); put("plays once when your phone turns on, then home appears", "toca uma vez quando o celular liga, depois vem a tela inicial")
        put("Design", "Design"); put("all four follow your accent color", "os quatro seguem sua cor de destaque"); put("pixel walk", "passeio pixel"); put("minimal", "mínimo")
        put("rintos 1.4 · boot", "rintos 1.4 · inicialização"); put("[ ok ] kernel says hi", "[ ok ] o kernel mandou oi"); put("[ ok ] waking up Rin", "[ ok ] acordando o Rin")
        put("[ ok ] loading your wallpaper", "[ ok ] carregando seu papel de parede"); put("[ ok ] icons: polished", "[ ok ] ícones: polidos"); put("[ ok ] widgets: stretching", "[ ok ] widgets: se alongando")
        put("[ ok ] notch: in position", "[ ok ] notch: em posição"); put("[ ok ] music: ready when you are", "[ ok ] música: pronta quando você quiser"); put("[ ok ] welcome back.", "[ ok ] bem-vindo de volta.")
        put("still everything you love", "tudo que você ama continua aqui"); put("your home", "sua tela inicial"); put("music + live lyrics", "música + letra ao vivo")
        put("UPGRADING", "ATUALIZANDO"); put("NEW", "NOVO"); put("the notch, in every app", "o notch, em todo app"); put("Rin remembers you", "o Rin lembra de você")
        put("live wallpapers", "papéis de parede animados"); put("new wallpapers", "novos papéis de parede"); put("5 new widgets", "5 widgets novos")
        put("your lock screen, your way", "sua tela de bloqueio, do seu jeito"); put("startup screens · português", "telas de inicialização · português")
        put("rebuilt.", "refeito."); put("for you.", "pra você."); put("40+ animations", "40+ animações")
        put("celebrate", "comemorar"); put("sing", "cantar"); put("shred", "solar"); put("spin", "girar"); put("snack", "lanchar"); put("purr", "ronronar")
        put("wink", "piscar"); put("laugh", "rir"); put("proud", "orgulho"); put("dance", "dançar"); put("scared", "susto"); put("cheer", "vibrar")
        put("on it. lofi, coming up.", "deixa comigo. lofi saindo."); put("remember my exam is on friday", "lembra que minha prova é sexta")
        put("got it. good luck, you've got this.", "anotado. boa sorte, você consegue."); put("MEMORY", "MEMÓRIA"); put("exam on friday", "prova na sexta")
        put("notch in every app", "notch em todo app"); put("…and it's still free", "…e continua grátis")
        put("Clock font", "Fonte do relógio"); put("Clock size", "Tamanho do relógio"); put("Clock position", "Posição do relógio"); put("Greeting", "Saudação"); put("good morning / good evening above the clock", "bom dia / boa noite acima do relógio")
        put("Memory", "Memória"); put("Rin remembers you", "O Rin lembra de você"); put("facts you tell him and your app habits, kept only on this phone", "o que você conta pra ele e seus hábitos de apps, guardados só neste celular")
        put("See what Rin remembers", "Ver o que o Rin lembra"); put("delete anything you don't want him to know", "apague o que você não quer que ele saiba")
        put("Forget everything", "Esquecer tudo"); put("Rin forgot everything", "O Rin esqueceu tudo"); put("What Rin remembers", "O que o Rin lembra")
        put("Nothing yet. Tell Rin things like “remember my exam is on Friday”.", "Nada ainda. Diga ao Rin coisas como “lembra que minha prova é sexta”.")
        put("forget", "esquecer"); put("HABITS HE NOTICED", "HÁBITOS QUE ELE NOTOU"); put("remembering", "lembrando"); put("forgetting", "esquecendo")
        put("use %s for the query", "use %s no lugar da busca"); put("e.g. EEE d MMM · dd/MM/yyyy", "ex.: EEE d MMM · dd/MM/yyyy")
    }

    private fun HashMap<String, String>.widgets() {
        put("good morning", "bom dia"); put("good afternoon", "boa tarde"); put("good evening", "boa noite"); put("late night mode", "modo madrugada")
        put("adores you", "te adora"); put("write something…", "escreva algo…")
        put("drink water", "beber água"); put("make RintOS mine", "deixar o RintOS do meu jeito"); put("install RintOS", "instalar o RintOS")
        put("to do", "a fazer"); put("add…", "adicionar…")
        put("love", "amor"); put("fed", "alimentado"); put("sleeping", "dormindo"); put("hungry...", "com fome..."); put("happy", "feliz")
        put("curious", "curioso"); put("feed", "alimentar"); put("note", "nota"); put("tally", "contador")
        put("heads", "cara"); put("tails", "coroa"); put("off", "desligada"); put("charging", "carregando"); put("battery", "bateria")
        put("Photos", "Fotos"); put("tap to pick your favorites", "toque pra escolher suas favoritas")
        put("partly cloudy", "parcialmente nublado"); put("WEATHER", "CLIMA"); put("type your city…", "digite sua cidade…"); put("fetching sky…", "consultando o céu…")
        put("clear", "céu limpo"); put("overcast", "nublado"); put("fog", "neblina"); put("rain", "chuva"); put("snow", "neve"); put("storm", "tempestade")
        put("Blocky tty clock and 6 other faces", "Relógio tty em blocos e mais 6 estilos")
        put("A real player with live synced lyrics", "Um player de verdade com letra sincronizada")
        put("A slideshow of pictures you love", "Um slideshow das fotos que você ama")
        put("Pet him. Feed him. He remembers.", "Faça carinho. Dê comida. Ele lembra.")
        put("Ask Rin", "Pergunte ao Rin"); put("Your AI assistant, one tap away", "Seu assistente de IA a um toque")
        put("Sticky note", "Nota adesiva"); put("Type right on your home screen", "Escreva direto na tela inicial")
        put("Checklist", "Lista de tarefas"); put("Tick things off without opening an app", "Marque tarefas sem abrir app")
        put("Focus timer", "Timer de foco"); put("Pomodoro with a ring you can spin", "Pomodoro com um anel que você gira")
        put("A whole calculator. On your home screen.", "Uma calculadora inteira. Na sua tela inicial.")
        put("Tally", "Contador"); put("Count anything: water, reps, days", "Conte qualquer coisa: água, repetições, dias")
        put("Dice & coin", "Dado e moeda"); put("Settle arguments fast", "Resolva discussões rápido")
        put("Flashlight", "Lanterna"); put("One tap torch", "Lanterna com um toque")
        put("Charge ring with time estimate", "Anel de carga com estimativa de tempo")
        put("Month", "Mês"); put("This month at a glance", "Este mês num relance")
        put("Weather", "Clima"); put("Open-Meteo, no account needed", "Open-Meteo, sem precisar de conta")
        put("Countdown", "Contagem regressiva"); put("Days until the thing you're waiting for", "Dias até aquilo que você está esperando")
        put("World clock", "Relógio mundial"); put("Another city's time. Tap to switch", "A hora de outra cidade. Toque pra trocar")
        put("Rin's thought", "Pensamento do Rin"); put("A new little thought from Rin every day", "Um pensamentinho novo do Rin todo dia")
        put("Device", "Aparelho"); put("Storage, memory and uptime", "Armazenamento, memória e tempo ligado")
        put("Stopwatch", "Cronômetro"); put("Start, pause, lap", "Iniciar, pausar, volta")
        put("countdown", "contagem"); put("tap to set a date", "toque pra escolher a data"); put("today!", "é hoje!"); put("day left", "dia restante"); put("days left", "dias restantes")
        put("what's coming up?", "o que vem aí?"); put("rin says", "o rin diz"); put("storage", "armazenamento"); put("memory", "memória")
        put("running", "rodando"); put("stopwatch", "cronômetro"); put("night", "noite"); put("day", "dia")
    }

    /** Settings choices show enum names, lowercased with spaces. */
    private fun HashMap<String, String>.enums() {
        listOf(
            "light" to "claro", "dark" to "escuro", "system" to "sistema", "serif" to "serifa",
            "art" to "arte", "solid" to "sólido", "gradient" to "gradiente", "mesh" to "aurora", "photo" to "foto", "live" to "animado",
            "tide" to "maré", "pixel night" to "noite pixel", "paper" to "papel",
            "slide" to "deslizar", "cube" to "cubo", "stack" to "pilha", "flip" to "virar", "fade" to "esmaecer", "carousel" to "carrossel", "tilt" to "inclinar",
            "dots" to "pontos", "line" to "linha", "numbers" to "números", "paw" to "patinha", "none" to "nenhum",
            "top" to "topo", "bottom" to "embaixo", "hidden" to "oculto",
            "compact" to "compacto", "pill" to "pílula", "glass" to "vidro", "underline" to "sublinhado",
            "circle" to "círculo", "rounded" to "arredondado", "square" to "quadrado", "teardrop" to "gota", "hexagon" to "hexágono",
            "pebble" to "seixo", "clover" to "trevo", "diamond" to "diamante",
            "grayscale" to "tons de cinza", "tinted" to "tingido", "outline" to "contorno",
            "white" to "branco", "black" to "preto", "alternate" to "alternado",
            "shrink" to "encolher", "bounce" to "quicar", "glow" to "brilhar", "wobble" to "balançar",
            "floating" to "flutuante", "grid" to "grade", "list" to "lista", "alphabet" to "alfabeto", "paged" to "páginas",
            "alpha" to "a–z", "install date" to "data de instalação", "most used" to "mais usados", "custom" to "personalizado",
            "drawer" to "gaveta", "lock" to "bloquear", "flashlight" to "lanterna", "music" to "música",
            "widgets" to "widgets", "first page" to "primeira página", "launch app" to "abrir app", "mascot" to "mascote",
            "camera" to "câmera", "assistant" to "assistente", "quick settings" to "configurações rápidas",
            "island" to "ilha", "wide" to "largo", "dot" to "ponto", "tab" to "aba",
            "time" to "hora", "now playing" to "tocando agora", "date" to "data",
            "blocks" to "blocos", "thin" to "fino", "stacked" to "empilhado", "words" to "por extenso", "analog" to "analógico",
            "start" to "início", "center" to "centro", "end" to "fim",
            "shy" to "tímido", "clingy" to "grudento", "smooth" to "suave",
            "scale up" to "ampliar", "clip reveal" to "revelar",
            "classic" to "clássico", "minimal" to "mínimo", "poster" to "pôster",
            "slide up" to "deslizar pra cima", "split" to "dividir",
            "neon" to "neon", "middle" to "meio", "blurred" to "desfocado", "same as theme" to "igual ao tema",
            "starfield" to "estrelas", "waves" to "ondas", "sunset" to "pôr do sol", "synthwave" to "synthwave", "peaks" to "montanhas",
        ).forEach { (k, v) -> putIfAbsent(k, v) }
    }
}
