package com.yuan3271.cloudrift.engine.english

import com.yuan3271.cloudrift.engine.Candidate
import com.yuan3271.cloudrift.engine.CandidateKind
import com.yuan3271.cloudrift.engine.EngineKind
import com.yuan3271.cloudrift.engine.EngineOutput
import com.yuan3271.cloudrift.engine.InputEngine

/**
 * English needs no conversion, so this engine only supplies word completion from a compact
 * built in list. Everything else (capitalisation, double space to period) is keyboard
 * behaviour and lives in the input controller.
 */
class EnglishEngine : InputEngine {

    override val kind: EngineKind = EngineKind.Latin

    override fun evaluate(raw: String, limit: Int): EngineOutput {
        if (raw.length < 2) return EngineOutput(literalCandidates(raw))
        val lower = raw.lowercase()
        val matches = ArrayList<Candidate>(limit)
        for (word in WORDS) {
            if (word.startsWith(lower) && word != lower) {
                matches.add(
                    Candidate(
                        text = matchCase(raw, word),
                        consumed = raw.length,
                        kind = CandidateKind.Prediction,
                        score = 1_000 - word.length,
                    ),
                )
                if (matches.size >= limit) break
            }
        }
        if (matches.isEmpty()) return EngineOutput(literalCandidates(raw))
        // The literal buffer is always offered first so the user is never forced to accept a
        // completion.
        return EngineOutput(literalCandidates(raw) + matches)
    }

    private fun literalCandidates(raw: String): List<Candidate> =
        if (raw.isEmpty()) {
            emptyList()
        } else {
            listOf(Candidate(raw, raw.length, CandidateKind.Raw, score = Int.MAX_VALUE))
        }

    private fun matchCase(raw: String, word: String): String = when {
        raw.all { it.isUpperCase() } -> word.uppercase()
        raw.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }

    companion object {
        /** Compact completion list: everyday words plus keyboard / technical vocabulary. */
        private val WORDS: List<String> = """
            a about above accept account action add address after again against age ago agree air
            all allow almost alone along already also although always among amount and another answer
            any anyone anything appear apply app apple application approach area argument arm around
            arrive art article artist ask assume attack attention available avoid away baby back bad
            bag balance ball bank bar base basic battery be beautiful because become bed before begin
            behavior behind believe below best better between beyond big bill bit black block blue board
            body book both box boy break bring brother build business but buy button by call camera
            can cancel candidate capital car card care carry case cat catch cause cell center century
            certain chair chance change channel character charge chat check child choice choose city
            claim class clean clear click client close cloud code cold collect college color come comment
            common company compare complete computer concern condition configuration confirm connect
            consider contact contain content context continue control conversation cook copy correct
            cost could country couple course cover create credit crime cross culture current cut customer
            dark data date day dead deal death debate decade decide decision deep default define degree
            delete deliver demand department depend describe design detail develop device dictionary
            difference different difficult digital direct direction discover discuss display distance
            do document dog door double down download draw dream drive drop during each early earth easy
            eat economy edge edit education effect effort eight either element else email employee end
            energy engine english enjoy enough enter entire environment equal error escape especially
            essential even evening event ever every everyone everything evidence exact example except
            exchange executive exist expect experience explain express extend extra eye face fact factor
            fail fall family far fast father fear feature feedback feel few field fight figure file fill
            film final find fine finger finish fire first fish fit five fix floor flow focus follow food
            foot for force foreign forget form forward found four free friend from front full function
            fund future game garden gas general generate get girl give glass global go goal good google
            government great green ground group grow growth guess guest guide gun guy hair half hand hang
            happen happy hard have he head health hear heart heat heavy help her here high history hit
            hold home hope hospital hot hotel hour house how however human hundred hurt idea identify if
            image imagine impact important improve include income increase indeed indicate industry
            information input inside instead install instance instead interest interface internal
            international internet interview into introduce invest invite involve issue it item its job
            join just keep key keyboard kind know knowledge language large last late later laugh law lead
            learn least leave left leg less let letter level life light like line link list listen little
            live load local location lock log long look lose lot love low machine main maintain major
            make man manage manager many map market marriage material matter may maybe me mean measure
            media medical meet member memory mention message method middle might military million mind
            minute miss mission model modern moment money month more morning most mother motion move
            movie much music must my name national natural nature near necessary need network never new
            news next nice night no node none normal north not note nothing notice now number object
            observe obtain obvious occasion occur ocean of off offer office officer official often oil ok
            old on once one only open operation opinion option or order organization other our out outside
            over own owner package page pain painting paper parent park part particular party pass past
            path patient pattern pay peace people per perform perhaps period person phone photo physical
            pick picture piece place plan plant play player please point police policy political poor
            popular position positive possible power practice prepare present president press pressure
            pretty prevent price primary print private probably problem process produce product program
            project property protect provide public pull purpose push put quality question quick quiet
            quite radio raise range rate rather reach read ready real reality realize really reason
            receive recent recognize record red reduce refer reflect region register regular relate
            relationship release remain remember remove repeat replace reply report represent request
            require research resource respond response responsibility rest result return reveal review
            rich right rise risk road rock role room root round rule run safe same save say scene school
            science score screen search season seat second secret section security see seek seem select
            sell send sense sentence series serious serve service session set setting settle seven
            several sex shake share she short should shoulder show side sign significant similar simple
            simply since sing single sir sister sit situation six size skill skin small smart smile so
            social society software soldier some someone something sometimes son song soon sorry sort
            sound source south space speak special specific speech speed spend sport spot spread spring
            staff stage stand standard star start state statement station stay step still stock stop
            storage store story straight strategy street strong structure student study stuff style
            subject success such sudden suffer suggest summer sun support sure surface system table take
            talk task tax teach teacher team technical technology telephone television tell ten term
            test text than thank that the their them theme themselves then theory there these they thing
            think third this those though thought thousand threat three through throw thus time to today
            together tonight too top total touch toward town track trade traffic train transfer travel
            treat tree trial trip trouble true trust truth try turn twice two type under understand
            unit until up update upgrade upon us use user usually value various version very via view
            village visit voice vote wait walk wall want war warm watch water way we wear weather web
            week weight welcome well west what whatever when where whether which while white who whole
            why wide wife will win wind window wish with within without woman wonder word work worker
            world worry would write writer wrong yard yeah year yes yet you young your yourself
        """.trimIndent()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .distinct()
    }
}
