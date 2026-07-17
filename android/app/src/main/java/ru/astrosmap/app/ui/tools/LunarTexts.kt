package ru.astrosmap.app.ui.tools

import ru.astrosmap.app.ui.AstroLabels

/** Тексты лунного календаря — порт LUNAR_PHASE_ADVICE / MOON_IN_SIGN_MOOD сайта (backend/interpretations.py). */
object LunarTexts {

    private fun g(pair: Pair<String, String>) = if (AstroLabels.isRu()) pair.first else pair.second

    val phaseEmoji = mapOf(
        "New Moon" to "🌑", "Waxing Crescent" to "🌒", "First Quarter" to "🌓",
        "Waxing Gibbous" to "🌔", "Full Moon" to "🌕", "Waning Gibbous" to "🌖",
        "Last Quarter" to "🌗", "Waning Crescent" to "🌘",
    )

    private val phaseNames = mapOf(
        "New Moon" to ("Новолуние" to "New Moon"),
        "Waxing Crescent" to ("Растущий серп" to "Waxing Crescent"),
        "First Quarter" to ("Первая четверть" to "First Quarter"),
        "Waxing Gibbous" to ("Растущая Луна" to "Waxing Gibbous"),
        "Full Moon" to ("Полнолуние" to "Full Moon"),
        "Waning Gibbous" to ("Убывающая Луна" to "Waning Gibbous"),
        "Last Quarter" to ("Последняя четверть" to "Last Quarter"),
        "Waning Crescent" to ("Убывающий серп" to "Waning Crescent"),
    )

    fun phaseName(key: String): String = phaseNames[key]?.let { g(it) } ?: key

    private val phaseAdvice = mapOf(
        "New Moon" to ("Время начинаний: загадывайте цели и «сажайте семена» новых дел. Энергии пока мало — не перегружайтесь, наметьте планы." to
            "A time for beginnings: set intentions and plant the seeds of new undertakings. Energy is still low — don't overload, sketch out plans."),
        "Waxing Crescent" to ("Первые шаги к задуманному: набирайте обороты, преодолевайте сомнения и инерцию, укрепляйте начатое." to
            "First steps toward your aim: gather momentum, overcome doubt and inertia, reinforce what you've begun."),
        "First Quarter" to ("Точка действия: решайте задачи, преодолевайте препятствия, проявляйте волю. Возможно напряжение — оно толкает вперёд." to
            "A point of action: tackle tasks, push through obstacles, exert your will. Tension may rise — it drives you forward."),
        "Waxing Gibbous" to ("Доработка и доводка: уточняйте детали, исправляйте, готовьтесь к результату. Хорошо для анализа и улучшений." to
            "Refinement and fine-tuning: polish details, correct, prepare for the result. Good for analysis and improvement."),
        "Full Moon" to ("Кульминация и результаты: пик энергии и эмоций, ясность — но и обострение. Хорошо завершать, подводить итоги и праздновать." to
            "Culmination and results: a peak of energy and emotion, clarity — but also heightened sensitivity. Good for completing, summing up and celebrating."),
        "Waning Gibbous" to ("Время делиться и отдавать: благодарность, обучение других, осмысление достигнутого, наведение порядка в делах." to
            "A time to share and give back: gratitude, teaching others, making sense of what's achieved, tidying up affairs."),
        "Last Quarter" to ("Расчистка и пересмотр: отпускайте лишнее, завершайте начатое, исправляйте ошибки, освобождайте место новому." to
            "Clearing and review: let go of the excess, finish what's begun, correct mistakes, make room for the new."),
        "Waning Crescent" to ("Отдых и восстановление: замедлитесь, отпустите старое, копите силы и подводите итог цикла перед новолунием." to
            "Rest and recovery: slow down, release the old, gather strength and close the cycle before the new moon."),
    )

    fun phaseAdvice(key: String): String = phaseAdvice[key]?.let { g(it) } ?: ""

    private val moonMood = mapOf(
        "Ari" to ("День энергичный и импульсивный — тянет действовать, начинать и лидировать; легко вспылить, держите темп под контролем." to
            "An energetic, impulsive day — you're drawn to act, start and lead; tempers flare easily, keep the pace in check."),
        "Tau" to ("День спокойный и чувственный — хочется уюта, вкусной еды и надёжности; спешка и перемены сегодня в тягость." to
            "A calm, sensual day — you crave comfort, good food and stability; haste and change feel burdensome."),
        "Gem" to ("День лёгкий и общительный — много контактов, информации и поездок; внимание скачет, трудно сосредоточиться." to
            "A light, sociable day — lots of contacts, information and short trips; attention darts about, hard to focus."),
        "Can" to ("День чувствительный и домашний — тянет к близким, заботе и уюту; выше ранимость и потребность в безопасности." to
            "A sensitive, homey day — you're drawn to loved ones, care and comfort; sensitivity and the need for safety rise."),
        "Leo" to ("День яркий и щедрый — хочется творить, блистать и получать признание; берегитесь гордости и жажды внимания." to
            "A bright, generous day — you want to create, shine and be recognised; watch for pride and a craving for attention."),
        "Vir" to ("День практичный и аккуратный — удобно наводить порядок, заниматься делами, здоровьем и мелочами; не придирайтесь к себе." to
            "A practical, tidy day — good for order, chores, health and details; don't be too hard on yourself."),
        "Lib" to ("День про отношения и красоту — тянет к гармонии, партнёрству и эстетике; решения даются трудно, хочется компромисса." to
            "A day of relationships and beauty — drawn to harmony, partnership and aesthetics; decisions come hard, you seek compromise."),
        "Sco" to ("День глубокий и напряжённый — сильные чувства и тяга к самой сути; возможны страсти, ревность и желание контроля." to
            "A deep, intense day — strong feelings and a pull toward the core; passion, jealousy and a wish for control may surface."),
        "Sag" to ("День оптимистичный и свободный — тянет к путешествиям, учёбе, простору и приключениям; не разбрасывайтесь обещаниями." to
            "An optimistic, free day — drawn to travel, study, open space and adventure; don't scatter promises."),
        "Cap" to ("День деловой и собранный — удобно работать, строить планы и брать ответственность; следите, чтобы не зачерстветь." to
            "A businesslike, focused day — good for work, planning and taking responsibility; just don't turn cold."),
        "Aqu" to ("День необычный и независимый — тянет к новому, дружбе и идеям; нужна свобода, рутина раздражает." to
            "An unusual, independent day — drawn to the new, to friendship and ideas; you need freedom, routine irritates."),
        "Pis" to ("День мечтательный и чуткий — обострены интуиция и сочувствие; хочется уединения, творчества и покоя." to
            "A dreamy, sensitive day — intuition and compassion are heightened; you long for solitude, creativity and peace."),
    )

    fun moonMood(sign: String): String = moonMood[sign]?.let { g(it) } ?: ""
}
