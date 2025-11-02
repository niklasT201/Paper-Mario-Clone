package net.bagaja.mafiagame

/**
 * A static library object that holds the definitions for all procedural songs.
 * This keeps the main game file clean and organizes music data in one place.
 */
object ProceduralSongLibrary {

    fun getMafiaTheme(): ProceduralMusicGenerator {
        return ProceduralMusicGenerator(
            bpm = 75.0,
            kickPattern  = "----------------",
            snarePattern = "----x-------x---",
            hihatPattern = "x-x-x-x-x-x-x-x-",
            crashPattern = "----------------",
            bassPattern = arrayOf(
                ProceduralMusicGenerator.Note.A1, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.A1, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.D2, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.D2, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.E2, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.E2, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.E2, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.E2, null, null, null, null, null, null, null
            ),
            chordStabPattern = arrayOf(
                ProceduralMusicGenerator.Note.A2, null, ProceduralMusicGenerator.Note.C3, null, ProceduralMusicGenerator.Note.E3, null, null, null,
                null, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.D2, null, ProceduralMusicGenerator.Note.F2, null, ProceduralMusicGenerator.Note.A2, null, null, null,
                null, null, null, null, null, null, null, null,
                ProceduralMusicGenerator.Note.E2, null, ProceduralMusicGenerator.Note.GS2, null, ProceduralMusicGenerator.Note.B2, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
            ),
            leadRiffPattern = emptyArray(),
            tensionPulsePattern = arrayOf(
                ProceduralMusicGenerator.Note.A3, null, null, null, ProceduralMusicGenerator.Note.E4, null, null, null,
                ProceduralMusicGenerator.Note.D4, null, null, null, ProceduralMusicGenerator.Note.C4, null, null, null,
                ProceduralMusicGenerator.Note.B3, null, null, null, null, null, null, null,
                null, null, ProceduralMusicGenerator.Note.C4, null, ProceduralMusicGenerator.Note.A3, null, null,
                ProceduralMusicGenerator.Note.G3, null, null, null, ProceduralMusicGenerator.Note.E4, null, null, null,
                ProceduralMusicGenerator.Note.D4, null, null, null, ProceduralMusicGenerator.Note.C4, null, null, null,
                ProceduralMusicGenerator.Note.B4, null, null, null, ProceduralMusicGenerator.Note.A4, null, ProceduralMusicGenerator.Note.B4, null,
                ProceduralMusicGenerator.Note.C5, null, null, null, null, null, null, null
            )
        )
    }

    fun getActionTheme(): ProceduralMusicGenerator {
        return ProceduralMusicGenerator(
            bpm = 145.0,
            kickPattern  = "x---x---x---x---",
            snarePattern = "----x-------x---",
            hihatPattern = "x-x-x-x-x-x-x-x-",
            crashPattern = "x---------------x---------------x---------------x---------------",
            bassPattern = arrayOf(ProceduralMusicGenerator.Note.E2, ProceduralMusicGenerator.Note.E2, null, ProceduralMusicGenerator.Note.E3, null, ProceduralMusicGenerator.Note.E2, null, null, ProceduralMusicGenerator.Note.E2, ProceduralMusicGenerator.Note.E2, null, ProceduralMusicGenerator.Note.E3, null, ProceduralMusicGenerator.Note.E2, null, null, ProceduralMusicGenerator.Note.A1, ProceduralMusicGenerator.Note.A1, null, ProceduralMusicGenerator.Note.A2, null, ProceduralMusicGenerator.Note.A1, null, null, ProceduralMusicGenerator.Note.A1, ProceduralMusicGenerator.Note.A1, null, ProceduralMusicGenerator.Note.A2, null, ProceduralMusicGenerator.Note.A1, null, null, ProceduralMusicGenerator.Note.D2, ProceduralMusicGenerator.Note.D2, null, ProceduralMusicGenerator.Note.D3, null, ProceduralMusicGenerator.Note.D2, null, null, ProceduralMusicGenerator.Note.D2, ProceduralMusicGenerator.Note.D2, null, ProceduralMusicGenerator.Note.D3, null, ProceduralMusicGenerator.Note.D2, null, null, ProceduralMusicGenerator.Note.A1, ProceduralMusicGenerator.Note.A1, null, ProceduralMusicGenerator.Note.A2, null, ProceduralMusicGenerator.Note.A1, ProceduralMusicGenerator.Note.A2, null, ProceduralMusicGenerator.Note.A1, ProceduralMusicGenerator.Note.A1, null, ProceduralMusicGenerator.Note.A2, null, ProceduralMusicGenerator.Note.A1, ProceduralMusicGenerator.Note.A2, ProceduralMusicGenerator.Note.A2),
            chordStabPattern = arrayOf(ProceduralMusicGenerator.Note.E3, null, null, ProceduralMusicGenerator.Note.B3, null, null, null, null, ProceduralMusicGenerator.Note.E3, null, null, ProceduralMusicGenerator.Note.B3, null, null, null, null, ProceduralMusicGenerator.Note.A3, null, null, ProceduralMusicGenerator.Note.E4, null, null, null, null, ProceduralMusicGenerator.Note.A3, null, null, ProceduralMusicGenerator.Note.E4, null, null, null, null, ProceduralMusicGenerator.Note.D3, null, null, ProceduralMusicGenerator.Note.A3, null, null, null, null, ProceduralMusicGenerator.Note.D3, null, null, ProceduralMusicGenerator.Note.A3, null, null, null, null, ProceduralMusicGenerator.Note.A3, null, null, ProceduralMusicGenerator.Note.E4, null, null, ProceduralMusicGenerator.Note.A3, null, ProceduralMusicGenerator.Note.A3, null, ProceduralMusicGenerator.Note.E4, null, ProceduralMusicGenerator.Note.A3, null, ProceduralMusicGenerator.Note.E4, null),
            leadRiffPattern = arrayOf(ProceduralMusicGenerator.Note.E4, null, ProceduralMusicGenerator.Note.FS4, ProceduralMusicGenerator.Note.G4, null, ProceduralMusicGenerator.Note.FS4, ProceduralMusicGenerator.Note.E4, null, ProceduralMusicGenerator.Note.D4, null, ProceduralMusicGenerator.Note.E4, null, ProceduralMusicGenerator.Note.FS4, null, null, null, ProceduralMusicGenerator.Note.A4, null, ProceduralMusicGenerator.Note.G4, ProceduralMusicGenerator.Note.FS4, null, ProceduralMusicGenerator.Note.E4, ProceduralMusicGenerator.Note.D4, null, ProceduralMusicGenerator.Note.E4, null, ProceduralMusicGenerator.Note.FS4, null, ProceduralMusicGenerator.Note.A4, null, null, null, ProceduralMusicGenerator.Note.D4, null, ProceduralMusicGenerator.Note.E4, ProceduralMusicGenerator.Note.FS4, null, ProceduralMusicGenerator.Note.G4, ProceduralMusicGenerator.Note.A4, null, ProceduralMusicGenerator.Note.B4, null, ProceduralMusicGenerator.Note.A4, null, ProceduralMusicGenerator.Note.G4, null, null, null, ProceduralMusicGenerator.Note.A4, null, ProceduralMusicGenerator.Note.G4, null, ProceduralMusicGenerator.Note.FS4, null, ProceduralMusicGenerator.Note.E4, null, ProceduralMusicGenerator.Note.D4, null, ProceduralMusicGenerator.Note.E4, null, ProceduralMusicGenerator.Note.A3, ProceduralMusicGenerator.Note.B3, ProceduralMusicGenerator.Note.CS4, ProceduralMusicGenerator.Note.D4),
            tensionPulsePattern = arrayOf(ProceduralMusicGenerator.Note.E5, null, null, null, ProceduralMusicGenerator.Note.E5, null, null, null, ProceduralMusicGenerator.Note.E5, null, null, null, ProceduralMusicGenerator.Note.E5, null, null, null, ProceduralMusicGenerator.Note.A4, null, null, null, ProceduralMusicGenerator.Note.A4, null, null, null, ProceduralMusicGenerator.Note.A4, null, null, null, ProceduralMusicGenerator.Note.A4, null, null, null, ProceduralMusicGenerator.Note.D5, null, null, null, ProceduralMusicGenerator.Note.D5, null, null, null, ProceduralMusicGenerator.Note.D5, null, null, null, ProceduralMusicGenerator.Note.D5, null, null, null, ProceduralMusicGenerator.Note.A4, null, null, null, ProceduralMusicGenerator.Note.A4, null, null, null, ProceduralMusicGenerator.Note.A4, null, null, ProceduralMusicGenerator.Note.A4, null, ProceduralMusicGenerator.Note.A4, null, null)
        )
    }
}
