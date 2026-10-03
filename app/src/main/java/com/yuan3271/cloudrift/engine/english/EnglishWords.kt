package com.yuan3271.cloudrift.engine.english

/**
 * The built in English vocabulary. One list, two users:
 *
 *  - [EnglishEngine], the English layout's word completion;
 *  - [com.yuan3271.cloudrift.engine.pinyin.PinyinEngine], which offers the same words while the
 *    user is typing **pinyin** - "hello" and "computer" are not readings of any Chinese word, and
 *    someone who typed them meant the English word.
 */
object EnglishWords {

    /** Everyday words plus keyboard / technical vocabulary. */
    private val CORE: List<String> = """
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

    /**
     * 0.2.13 那张表是给**英文补全**用的精简表，`hello`、`thanks`、`tomorrow`、`password` 这些
     * 都不在里面；而中文模式下要打的恰恰是这种词。这里补的是人人每天都打的日常词（人工整理，
     * 和全角/半角符号表、[com.yuan3271.cloudrift.engine.pinyin.PinyinEngine] 的
     * `COMMON_WORDS` 同一个口径），不追规模、不追生僻。
     */
    private val EVERYDAY: List<String> = """
        hello hi hey thanks please sorry welcome goodbye bye alright okay ok cool awesome great
        congratulations cheers wow oops yeah yep nope hmm oh
        afternoon evening midnight weekend vacation holiday birthday party gift present
        tomorrow yesterday tonight
        breakfast lunch dinner snack pizza burger sandwich salad soup noodle noodle bread cake
        coffee tea milk juice beer wine water fruit apple banana orange grape lemon peach
        vegetable potato tomato onion carrot chicken beef pork egg salt sugar oil sauce
        restaurant menu bill supermarket market shop store mall money cash card wallet price
        discount receipt clothes shirt pants shoes dress jacket coat hat bag glasses watch
        phone laptop screen mouse battery charger headphone photo video music song movie game
        book notebook pen paper letter message house room kitchen bathroom bedroom garden
        door window wall floor table chair bed sofa street road bridge river mountain beach
        island forest tree flower grass leaf animal dog cat bird fish horse tiger lion monkey
        rabbit sheep cow pig duck weather rain snow wind sun moon star sky cloud storm thunder
        spring summer autumn winter noon season family father mother parents brother sister
        son daughter child children baby friend people person man woman women boy girl
        happy sad angry tired busy free ready easy hard simple difficult important necessary
        possible pretty cute lovely nice great good bad better best worse worst fast slow
        early late new old young big small long short high low hot cold warm cool dry wet
        clean dirty cheap expensive rich poor strong weak heavy light deep wide narrow thick
        thin full empty love like hate want need hope wish feel think know understand remember
        forget learn teach study work play run walk jump swim fly drive ride sleep wake eat
        drink cook wash clean buy sell pay cost spend save waste send receive give take bring
        carry hold keep leave stay arrive return start stop begin finish continue wait help
        support follow lead manage control change move turn push pull touch press drag drop
        catch throw find lose search look watch see listen hear speak talk say tell ask answer
        call meet visit invite join share show hide login logout username password account
        website folder document upload download install delete copy paste click print open
        close save what when where which who why how whether maybe perhaps actually probably
        definitely really very much many some any both each every several
    """.trimIndent()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    /** [CORE] 在前、[EVERYDAY] 在后，重复的词只留第一次出现的位置。 */
    val ALL: List<String> = (CORE + EVERYDAY).distinct()

    /**
     * [word] drawn the way the user typed it: all caps stay all caps, a leading capital stays.
     * Shared with the Chinese engine so "Hello" on the pinyin keyboard commits "Hello" and not
     * "hello".
     */
    fun matchCase(typed: String, word: String): String = when {
        typed.all { it.isUpperCase() } -> word.uppercase()
        typed.first().isUpperCase() -> word.replaceFirstChar { it.uppercaseChar() }
        else -> word
    }
}
