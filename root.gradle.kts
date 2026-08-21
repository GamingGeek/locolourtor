// https://github.com/EssentialGG/essential-gradle-toolkit
plugins {
    id("gg.essential.multi-version.root")
}

preprocess {
    // Integer version encoding:
    //   26.2    → 260200
    //   26.1    → 260100 (supports 26.1, 26.1.1, 26.1.2)
    //   1.21.11 → 12111
    //
    // Mapping types:
    //   "yarn"     – Fabric 1.21.11 uses Yarn (community-maintained)
    //   "official" – All 26.x versions and NeoForge use Mojang official mappings

    val neoforge262   = createNode("26.2-neoforge",   260200, "official")
    val fabric262     = createNode("26.2-fabric",     260200, "official")
    val neoforge261   = createNode("26.1-neoforge",   260100, "official")
    val fabric261     = createNode("26.1-fabric",     260100, "official")
    val neoforge12111 = createNode("1.21.11-neoforge",  12111, "official")
    val fabric12111   = createNode("1.21.11-fabric",    12111, "yarn")

    // The link chain tells the preprocessor how to diff code between adjacent
    // versions. Code flows: newest → oldest, NeoForge ↔ Fabric at each step.
    neoforge262.link(fabric262)
    fabric262.link(neoforge261)
    neoforge261.link(fabric261)
    fabric261.link(neoforge12111)
    neoforge12111.link(fabric12111)
}
