# -*- coding: utf-8 -*-
"""Профессиональный двуязычный (RU/EN) движок астрологических интерпретаций.

Каждая локализуемая строка хранится кортежем (ru, en); функции принимают lang.
"""
from __future__ import annotations

from typing import Optional

from . import constants as C


def g(pair, lang="ru"):
    """Возвращает en-вариант при lang=='en', иначе ru."""
    if isinstance(pair, (tuple, list)):
        return pair[1] if lang == "en" and len(pair) > 1 else pair[0]
    return pair


# --------------------------------------------------------------------------- #
#  Планеты: роль (за что отвечает)
# --------------------------------------------------------------------------- #
PLANET_ROLE = {
    "Sun": ("ядро личности, воля и жизненная цель", "the core of personality, will and life purpose"),
    "Moon": ("эмоции, инстинкты и потребность в безопасности", "emotions, instincts and the need for security"),
    "Mercury": ("мышление, речь и способ обмена информацией", "thinking, speech and the way of exchanging information"),
    "Venus": ("любовь, ценности и чувство гармонии", "love, values and the sense of harmony"),
    "Mars": ("энергия, воля к действию и умение отстаивать себя", "energy, the will to act and the ability to assert oneself"),
    "Jupiter": ("рост, мировоззрение, вера и расширение возможностей", "growth, worldview, faith and the expansion of possibilities"),
    "Saturn": ("дисциплина, ответственность и жизненная опора", "discipline, responsibility and inner backbone"),
    "Uranus": ("стремление к свободе, оригинальность и потребность в переменах", "the drive for freedom, originality and the need for change"),
    "Neptune": ("воображение, духовность и тонкая чувствительность", "imagination, spirituality and subtle sensitivity"),
    "Pluto": ("глубинная трансформация, воля к власти и перерождение", "deep transformation, the will to power and rebirth"),
    "Mean_North_Lunar_Node": ("вектор развития и кармические задачи будущего", "the vector of growth and the karmic tasks of the future"),
    "True_North_Lunar_Node": ("вектор развития и кармические задачи будущего", "the vector of growth and the karmic tasks of the future"),
    "Mean_South_Lunar_Node": ("врождённый багаж и зона комфорта прошлого", "inborn baggage and the comfort zone of the past"),
    "True_South_Lunar_Node": ("врождённый багаж и зона комфорта прошлого", "inborn baggage and the comfort zone of the past"),
    "Chiron": ("глубинная рана и дар целительства через её исцеление", "a deep wound and the gift of healing through healing it"),
    "Mean_Lilith": ("вытесненные желания, теневая страсть и точка бунта", "repressed desires, shadow passion and the point of rebellion"),
    "True_Lilith": ("вытесненные желания, теневая страсть и точка бунта", "repressed desires, shadow passion and the point of rebellion"),
    "Ascendant": ("личность, тело и жизненный курс", "the personality, the body and the life course"),
    "Medium_Coeli": ("карьеру, статус и публичную роль", "career, status and public role"),
    "Descendant": ("партнёрство и значимые отношения", "partnership and significant relationships"),
    "Imum_Coeli": ("дом, семью и внутренние основы", "home, family and inner foundations"),
}


def planet_role(name, lang="ru"):
    return g(PLANET_ROLE.get(name), lang) if PLANET_ROLE.get(name) else ""


# --------------------------------------------------------------------------- #
#  Прогностика: что приносит транзитная планета
# --------------------------------------------------------------------------- #
TRANSIT_THEME = {
    "Jupiter": ("расширение, рост, новые возможности и удачу", "expansion, growth, new opportunities and luck"),
    "Saturn": ("проверку на прочность, ответственность, структуру и взросление", "a test of endurance, responsibility, structure and maturing"),
    "Uranus": ("неожиданные перемены, свободу и прорывы", "unexpected change, freedom and breakthroughs"),
    "Neptune": ("вдохновение, идеализм, духовный поиск и размывание границ", "inspiration, idealism, spiritual search and the blurring of boundaries"),
    "Pluto": ("глубокую трансформацию, кризис и перерождение", "deep transformation, crisis and rebirth"),
    "Chiron": ("исцеление старых ран и работу с уязвимостью", "the healing of old wounds and work with vulnerability"),
    "Mean_North_Lunar_Node": ("поворот к новому опыту и точки судьбы", "a turn toward new experience and points of destiny"),
    "True_North_Lunar_Node": ("поворот к новому опыту и точки судьбы", "a turn toward new experience and points of destiny"),
    "Mean_South_Lunar_Node": ("проработку прошлого и кармические узлы", "working through the past and karmic knots"),
    "True_South_Lunar_Node": ("проработку прошлого и кармические узлы", "working through the past and karmic knots"),
    "Mars": ("всплеск энергии, активность и возможные конфликты", "a surge of energy, activity and possible conflicts"),
    "Sun": ("акцент внимания и активацию темы", "a focus of attention and the activation of the theme"),
    "Venus": ("тему отношений, ценностей и удовольствий", "the theme of relationships, values and pleasures"),
    "Mercury": ("переговоры, информацию и поездки", "negotiations, information and short trips"),
    "Moon": ("смену настроения и эмоциональные акценты дня", "shifts of mood and the emotional accents of the day"),
}

_ASPECT_CATEGORY = {
    "conjunction": "conjunction",
    "opposition": "tension",
    "square": "tension",
    "trine": "harmony",
    "sextile": "harmony",
}

_TRANSIT_GUIDANCE = {
    "conjunction": (
        "Тема выходит на первый план и активизируется напрямую — ключевой момент для осознанных шагов в этой сфере.",
        "The theme comes to the foreground and is activated directly — a key moment for conscious steps in this area.",
    ),
    "harmony": (
        "Благоприятное окно: энергия периода легко поддерживает эту сферу — удачное время действовать и развивать начатое.",
        "A favorable window: the energy of the period easily supports this area — a good time to act and develop what was begun.",
    ),
    "tension": (
        "Период проверки и роста: возникает напряжение, которое важно не избегать, а сознательно прорабатывать — оно ведёт к зрелости.",
        "A time of testing and growth: tension arises that is important not to avoid but to work through consciously — it leads to maturity.",
    ),
}


def interpret_transit(t_name: str, aspect: str, n_name: str, lang: str = "ru") -> str:
    theme = TRANSIT_THEME.get(t_name)
    role = PLANET_ROLE.get(n_name)
    category = _ASPECT_CATEGORY.get(aspect)
    if not theme or not role or not category:
        return ""
    t_ru = C.point_name(t_name, lang)
    n_ru = C.point_name(n_name, lang)
    aspect_ru = C.aspect_name(aspect, lang).lower()
    guidance = g(_TRANSIT_GUIDANCE[category], lang)
    if lang == "en":
        return (
            f"{t_ru} by transit brings {g(theme, lang)}. In a “{aspect_ru}” aspect to natal "
            f"“{n_ru}” it touches the area of “{g(role, lang)}”. {guidance}"
        )
    return (
        f"{t_ru} транзитом приносит {g(theme, lang)}. В аспекте «{aspect_ru}» к натальной точке "
        f"«{n_ru}» затрагивается сфера «{g(role, lang)}». {guidance}"
    )


def prog_moon_text(sign: str, house, lang: str = "ru") -> str:
    """Прогрессивная Луна — «эмоциональная глава» периода (~2,5 года)."""
    essence = g(SIGN_ARCHETYPE.get(sign, {}).get("essence", ("", "")), lang)
    hexp = g(HOUSE_EXP.get(house, ("", "")), lang) if house else ""
    sname = C.sign_name(sign, lang)
    if lang == "en":
        out = (f"For about 2–2.5 years your progressed Moon moves through {sname}: the emotional "
               f"“chapter” of this period is coloured by {essence}.")
        if hexp:
            out += f" Its focus falls on the {_ord_en(house)} house — {hexp}"
        return out
    out = (f"Около 2–2,5 лет прогрессивная Луна идёт по знаку {sname}: «эмоциональная глава» периода "
           f"окрашена в {essence}.")
    if hexp:
        out += f" Её фокус — в сфере {house}-го дома: {hexp}"
    return out


def prog_sun_text(sign: str, changed: bool, natal_sign: str, lang: str = "ru") -> str:
    """Прогрессивное Солнце — медленное вызревание/смена жизненного этапа."""
    light = g(SIGN_ARCHETYPE.get(sign, {}).get("light", ("", "")), lang)
    sname = C.sign_name(sign, lang)
    nname = C.sign_name(natal_sign, lang)
    if lang == "en":
        if changed:
            return (f"Your progressed Sun has moved from {nname} into {sname} — a slow but deep change of life "
                    f"phase. In the coming years these qualities come to the fore: {light}.")
        return (f"Your progressed Sun keeps developing through {sname}, gradually ripening its qualities: {light}.")
    if changed:
        return (f"Прогрессивное Солнце сменило знак: раньше — {nname}, теперь {C.sign_in(sign, lang)} — медленная, но глубокая смена жизненного "
                f"этапа. В ближайшие годы на первый план выходят качества: {light}.")
    return (f"Прогрессивное Солнце продолжает развиваться по знаку {sname}, постепенно раскрывая качества: {light}.")


# --------------------------------------------------------------------------- #
#  Знаки: грани проявления — манера, мотив, тень
# --------------------------------------------------------------------------- #
SIGN_FACETS = {
    "Ari": {
        "manner": ("напористо и прямо, через инициативу и быстрые решения", "assertively and directly, through initiative and quick decisions"),
        "motive": ("желание быть первым и действовать без промедления", "the desire to be first and to act without delay"),
        "shadow": ("импульсивность, нетерпеливость и склонность к конфликтам", "impulsiveness, impatience and a tendency toward conflict"),
    },
    "Tau": {
        "manner": ("устойчиво, размеренно и чувственно, опираясь на надёжность", "steadily, calmly and sensually, relying on reliability"),
        "motive": ("потребность в стабильности, комфорте и материальной опоре", "the need for stability, comfort and material security"),
        "shadow": ("упрямство, инертность и страх перемен", "stubbornness, inertia and fear of change"),
    },
    "Gem": {
        "manner": ("гибко, любознательно и общительно, через слово и информацию", "flexibly, curiously and sociably, through word and information"),
        "motive": ("жажда новых впечатлений, контактов и понимания", "a thirst for new impressions, contacts and understanding"),
        "shadow": ("поверхностность, разбросанность и непостоянство", "superficiality, scatteredness and inconstancy"),
    },
    "Can": {
        "manner": ("чутко и заботливо, опираясь на чувства, память и привязанности", "sensitively and caringly, relying on feelings, memory and attachments"),
        "motive": ("потребность в эмоциональной близости и чувстве дома", "the need for emotional closeness and a sense of home"),
        "shadow": ("обидчивость, тревожность и уход в защиту", "touchiness, anxiety and withdrawal into defense"),
    },
    "Leo": {
        "manner": ("ярко, творчески и великодушно, с потребностью быть замеченным", "brightly, creatively and generously, with a need to be noticed"),
        "motive": ("стремление к самовыражению, признанию и любви", "the drive for self-expression, recognition and love"),
        "shadow": ("гордыня, эгоцентризм и зависимость от похвалы", "pride, egocentrism and dependence on praise"),
    },
    "Vir": {
        "manner": ("тщательно, практично и аналитично, через пользу и порядок", "carefully, practically and analytically, through usefulness and order"),
        "motive": ("желание совершенствовать, служить и приносить пользу", "the desire to perfect, to serve and to be useful"),
        "shadow": ("придирчивость, тревога о деталях и самокритика", "fault-finding, anxiety over details and self-criticism"),
    },
    "Lib": {
        "manner": ("дипломатично и эстетично, ориентируясь на партнёрство и баланс", "diplomatically and aesthetically, oriented toward partnership and balance"),
        "motive": ("потребность в гармонии, справедливости и согласии", "the need for harmony, fairness and accord"),
        "shadow": ("нерешительность, зависимость от чужого мнения и избегание конфликтов", "indecision, dependence on others' opinions and avoidance of conflict"),
    },
    "Sco": {
        "manner": ("глубоко, страстно и бескомпромиссно, проникая в самую суть", "deeply, passionately and uncompromisingly, penetrating to the core"),
        "motive": ("стремление к подлинности, контролю и внутренней силе", "the drive for authenticity, control and inner power"),
        "shadow": ("ревность, подозрительность и разрушительные крайности", "jealousy, suspicion and destructive extremes"),
    },
    "Sag": {
        "manner": ("свободно, широко и оптимистично, в поиске смысла и горизонтов", "freely, broadly and optimistically, in search of meaning and horizons"),
        "motive": ("жажда свободы, истины и расширения границ", "a thirst for freedom, truth and the widening of boundaries"),
        "shadow": ("самоуверенность, нетерпение к деталям и склонность к проповеди", "overconfidence, impatience with details and a tendency to preach"),
    },
    "Cap": {
        "manner": ("ответственно, дисциплинированно и целеустремлённо", "responsibly, with discipline and determination"),
        "motive": ("стремление к результату, статусу и долговременной опоре", "the drive for results, status and a lasting foundation"),
        "shadow": ("жёсткость, пессимизм и подавление чувств ради дела", "rigidity, pessimism and the suppression of feelings for the sake of the goal"),
    },
    "Aqu": {
        "manner": ("независимо, оригинально и нестандартно, устремляясь в будущее", "independently, originally and unconventionally, reaching toward the future"),
        "motive": ("потребность в свободе, идеях и принадлежности к единомышленникам", "the need for freedom, ideas and belonging to like-minded people"),
        "shadow": ("отстранённость, упрямый бунт и эмоциональная холодность", "detachment, stubborn rebellion and emotional coldness"),
    },
    "Pis": {
        "manner": ("мечтательно, сострадательно и интуитивно, размывая границы", "dreamily, compassionately and intuitively, dissolving boundaries"),
        "motive": ("стремление к единству, состраданию и высшему смыслу", "the longing for unity, compassion and higher meaning"),
        "shadow": ("уход от реальности, жертвенность и размытость воли", "escape from reality, self-sacrifice and a blurred will"),
    },
}


def sign_manner(sign, lang="ru"):
    f = SIGN_FACETS.get(sign)
    return g(f["manner"], lang) if f else ""


# --------------------------------------------------------------------------- #
#  Дома: на что направлена энергия
# --------------------------------------------------------------------------- #
HOUSE_FOCUS = {
    1: ("на формирование личности, внешний облик и способ заявлять о себе миру", "toward shaping the personality, outward image and the way of presenting oneself to the world"),
    2: ("на деньги, ресурсы, таланты и систему личных ценностей", "toward money, resources, talents and the system of personal values"),
    3: ("на общение, обучение, ближнее окружение и повседневные связи", "toward communication, learning, the immediate circle and everyday connections"),
    4: ("на дом, семью, корни и внутреннюю опору", "toward home, family, roots and inner support"),
    5: ("на творчество, любовь, детей и радость самовыражения", "toward creativity, love, children and the joy of self-expression"),
    6: ("на работу, здоровье, рутину и служение", "toward work, health, routine and service"),
    7: ("на партнёрство, брак, союзы и отношения «один на один»", "toward partnership, marriage, alliances and one-to-one relationships"),
    8: ("на кризисы, трансформации, интимность и чужие ресурсы", "toward crises, transformation, intimacy and shared resources"),
    9: ("на мировоззрение, путешествия, высшее образование и поиск смысла", "toward worldview, travel, higher education and the search for meaning"),
    10: ("на карьеру, репутацию, призвание и положение в обществе", "toward career, reputation, vocation and standing in society"),
    11: ("на друзей, единомышленников, цели и сообщества", "toward friends, like-minded people, goals and communities"),
    12: ("на подсознание, уединение, тайны и духовную жизнь", "toward the subconscious, solitude, secrets and spiritual life"),
}


def house_focus(num, lang="ru"):
    return g(HOUSE_FOCUS.get(num), lang) if HOUSE_FOCUS.get(num) else ""


# --------------------------------------------------------------------------- #
#  Аспекты: характер взаимодействия функций
# --------------------------------------------------------------------------- #
ASPECT_INTERP = {
    "conjunction": ("сливаются в единую мощную функцию, усиливая и окрашивая друг друга — важно научиться сознательно управлять этой связкой", "merge into a single powerful function, amplifying and colouring each other — it is important to learn to manage this blend consciously"),
    "opposition": ("находятся в напряжении противоположностей, требуя осознанного баланса; часто проявляется через отношения и внешние ситуации", "stand in the tension of opposites, demanding conscious balance; often expressed through relationships and outer situations"),
    "trine": ("гармонично поддерживают друг друга, давая природный талант и лёгкость — но без усилий этот дар можно не реализовать", "harmoniously support each other, granting natural talent and ease — yet without effort this gift may go unrealized"),
    "square": ("вступают во внутренний конфликт, и это напряжение становится мощным двигателем роста через преодоление", "enter into inner conflict, and this tension becomes a powerful engine of growth through overcoming"),
    "sextile": ("дают благоприятные возможности и лёгкое сотрудничество, которое раскрывается при небольшом сознательном усилии", "offer favorable opportunities and easy cooperation that unfolds with a little conscious effort"),
    "quincunx": ("плохо согласуются и требуют постоянной тонкой подстройки и компромиссов", "fit together poorly and require constant fine adjustment and compromise"),
    "semisextile": ("связаны слабо, давая лёгкий, едва ощутимый импульс к взаимодействию", "are weakly linked, giving a light, barely perceptible impulse to interact"),
    "semisquare": ("создают подспудное раздражение и мелкое, но настойчивое напряжение", "create underlying irritation and minor but persistent tension"),
    "sesquiquadrate": ("несут скрытое напряжение, прорывающееся в кризисные моменты", "carry hidden tension that breaks through in moments of crisis"),
    "sesquisquare": ("несут скрытое напряжение, прорывающееся в кризисные моменты", "carry hidden tension that breaks through in moments of crisis"),
    "quintile": ("дают творческую искру и нестандартную одарённость", "give a creative spark and unconventional giftedness"),
    "biquintile": ("дают творческую искру и нестандартную одарённость", "give a creative spark and unconventional giftedness"),
}

# Авторские темы для пар планет: о чём встреча двух функций, что даёт в гармонии и в напряжении.
# Ключ — пара в порядке _PAIR_ORDER. Покрывает личные и социальные планеты (где аспект особенно личностный).
_PAIR_ORDER = ["Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn"]
ASPECT_PAIR = {
    ("Sun", "Moon"): {
        "theme": ("встреча сознательной воли и эмоциональных потребностей — «хочу» и «чувствую», мужского и женского начал внутри", "the meeting of conscious will and emotional needs — “I want” and “I feel”, the masculine and feminine within"),
        "harmony": ("цельность натуры: разум и сердце заодно, лёгкость в семье и в ладу с собой", "wholeness of nature: mind and heart in accord, ease in family and at peace with oneself"),
        "tension": ("разлад между желаниями и потребностями, разумом и чувствами; внутренний конфликт, нередко уходящий корнями в отношения родителей", "a rift between wants and needs, mind and feeling; an inner conflict often rooted in the parents' relationship"),
    },
    ("Sun", "Mercury"): {
        "theme": ("связь личности и мышления — насколько слово выражает суть", "the link of personality and thinking — how far the word expresses the essence"),
        "harmony": ("ясный ум, уверенная речь, совпадение того, кто вы есть, и того, что вы говорите", "a clear mind, confident speech, an alignment of who you are and what you say"),
        "tension": ("субъективность суждений и склонность переоценивать свои идеи, не слыша других", "subjectivity of judgement and a tendency to overvalue one's own ideas while not hearing others"),
    },
    ("Sun", "Venus"): {
        "theme": ("личность и способность любить, нравиться и ценить", "personality and the capacity to love, to please and to value"),
        "harmony": ("обаяние, артистизм, умение располагать к себе и здоровое чувство собственной ценности", "charm, artistry, the ability to win others over and a healthy sense of self-worth"),
        "tension": ("тщеславие, зависимость от одобрения и склонность потакать себе", "vanity, dependence on approval and a tendency toward self-indulgence"),
    },
    ("Sun", "Mars"): {
        "theme": ("воля и действие, эго и напор — как вы добиваетесь своего", "will and action, ego and drive — how you get what you want"),
        "harmony": ("сильная воля, смелость и энергия достигать целей, здоровая напористость", "strong will, courage and the energy to reach goals, healthy assertiveness"),
        "tension": ("вспыльчивость, импульсивность, борьба с авторитетами и перерасход сил", "a quick temper, impulsiveness, struggle with authority and burning out your strength"),
    },
    ("Sun", "Jupiter"): {
        "theme": ("личность и рост, вера в себя и в жизнь", "personality and growth, faith in oneself and in life"),
        "harmony": ("оптимизм, щедрость, широта натуры и природное везение", "optimism, generosity, breadth of nature and natural luck"),
        "tension": ("переоценка себя, самоуверенность и расточительность", "overestimating oneself, overconfidence and extravagance"),
    },
    ("Sun", "Saturn"): {
        "theme": ("эго и дисциплина, самовыражение и долг", "ego and discipline, self-expression and duty"),
        "harmony": ("самодисциплина, ответственность и умение строить надолго; зрелая опора на себя", "self-discipline, responsibility and the ability to build for the long term; a mature reliance on oneself"),
        "tension": ("строгий внутренний критик, неуверенность и давление долга; страх оказаться недостаточно хорошим", "a strict inner critic, insecurity and the pressure of duty; the fear of not being good enough"),
    },
    ("Moon", "Mercury"): {
        "theme": ("чувства и ум — как переживания связаны с мыслями и речью", "feeling and mind — how emotions connect with thoughts and speech"),
        "harmony": ("эмоциональный интеллект, чуткая речь и единство мысли и чувства", "emotional intelligence, sensitive speech and a unity of thought and feeling"),
        "tension": ("тревожность мешает думать ясно, переменчивость мнений и субъективность", "anxiety clouds clear thinking, with changeable opinions and subjectivity"),
    },
    ("Moon", "Venus"): {
        "theme": ("чувства и любовь, потребность в тепле и нежности", "feeling and love, the need for warmth and tenderness"),
        "harmony": ("нежность, обаяние, теплота в отношениях и любовь к красоте и уюту", "tenderness, charm, warmth in relationships and a love of beauty and comfort"),
        "tension": ("эмоциональная зависимость, капризность в любви и тяга потакать себе", "emotional dependence, moodiness in love and a pull toward self-indulgence"),
    },
    ("Moon", "Mars"): {
        "theme": ("чувства и желания, эмоции и гнев", "feeling and desire, emotion and anger"),
        "harmony": ("эмоциональная энергия, страстность и умение защищать своих", "emotional energy, passion and the ability to protect your own"),
        "tension": ("вспыльчивость, обидчивость, перепады настроения и импульсивные реакции", "a quick temper, touchiness, mood swings and impulsive reactions"),
    },
    ("Moon", "Jupiter"): {
        "theme": ("чувства и вера, эмоциональное изобилие", "feeling and faith, emotional abundance"),
        "harmony": ("душевная щедрость, оптимизм и тёплая заботливость", "generosity of soul, optimism and warm caring"),
        "tension": ("эмоциональные преувеличения, потворство себе и беспечность", "emotional exaggeration, self-indulgence and carelessness"),
    },
    ("Moon", "Saturn"): {
        "theme": ("потребность в безопасности и ограничения, чувства и долг", "the need for safety and limitation, feeling and duty"),
        "harmony": ("эмоциональная зрелость, надёжность, верность и выдержка", "emotional maturity, reliability, loyalty and self-control"),
        "tension": ("эмоциональная зажатость, чувство недолюбленности и страх отвержения", "emotional reserve, a sense of being unloved and the fear of rejection"),
    },
    ("Mercury", "Venus"): {
        "theme": ("ум и вкус, мышление и чувство гармонии", "mind and taste, thinking and the sense of harmony"),
        "harmony": ("обаятельная речь, чувство стиля, дипломатичность и артистизм", "charming speech, a sense of style, diplomacy and artistry"),
        "tension": ("склонность приукрашивать и нерешительность в словах и выборе", "a tendency to embellish and indecision in words and choices"),
    },
    ("Mercury", "Mars"): {
        "theme": ("ум и действие, мысль и напор", "mind and action, thought and drive"),
        "harmony": ("острый ум, быстрые решения, находчивость и убедительность", "a sharp mind, quick decisions, resourcefulness and persuasiveness"),
        "tension": ("резкость в словах, споры, поспешные суждения и раздражительность", "sharpness in words, arguments, hasty judgements and irritability"),
    },
    ("Mercury", "Jupiter"): {
        "theme": ("ум и кругозор, детали и общая картина", "mind and horizon, detail and the big picture"),
        "harmony": ("широта мышления, тяга к знаниям и дар учить и убеждать", "breadth of mind, a thirst for knowledge and a gift for teaching and persuading"),
        "tension": ("разбросанность, обещания сверх меры и пренебрежение деталями", "scatteredness, over-promising and neglect of details"),
    },
    ("Mercury", "Saturn"): {
        "theme": ("ум и структура, мышление и дисциплина", "mind and structure, thinking and discipline"),
        "harmony": ("глубокое, методичное и серьёзное мышление, концентрация и память", "deep, methodical and serious thinking, concentration and memory"),
        "tension": ("пессимизм, медлительность, страх ошибиться и скованность в речи", "pessimism, slowness, fear of error and constraint in speech"),
    },
    ("Venus", "Mars"): {
        "theme": ("любовь и страсть, нежность и влечение", "love and passion, tenderness and attraction"),
        "harmony": ("гармония нежности и страсти, обаяние и сильная творческая энергия", "a harmony of tenderness and passion, charm and strong creative energy"),
        "tension": ("конфликт между нежностью и желанием, бурные отношения и ревность", "a conflict between tenderness and desire, stormy relationships and jealousy"),
    },
    ("Venus", "Jupiter"): {
        "theme": ("любовь и изобилие, ценности и щедрость", "love and abundance, values and generosity"),
        "harmony": ("щедрость в любви, удача в деньгах и отношениях, любовь к радости жизни", "generosity in love, luck in money and relationships, a love of life's joys"),
        "tension": ("потакание себе, расточительность и склонность к излишествам", "self-indulgence, extravagance and a leaning toward excess"),
    },
    ("Venus", "Saturn"): {
        "theme": ("любовь и обязательства, чувства и зрелость", "love and commitment, feeling and maturity"),
        "harmony": ("верность, серьёзные отношения, надёжность и чувство меры", "loyalty, serious relationships, reliability and a sense of measure"),
        "tension": ("страх отвержения, холодность или скупость в чувствах, ощущение недостойности любви", "fear of rejection, coldness or stinginess in feeling, a sense of being unworthy of love"),
    },
    ("Mars", "Jupiter"): {
        "theme": ("действие и масштаб, энергия и размах", "action and scale, energy and scope"),
        "harmony": ("энергия плюс размах, предприимчивость, азарт и удача в начинаниях", "energy plus scope, enterprise, drive and luck in undertakings"),
        "tension": ("авантюризм, перерасход сил, рискованность и самоуверенность в действиях", "recklessness, burning out, risk-taking and overconfidence in action"),
    },
    ("Mars", "Saturn"): {
        "theme": ("действие и контроль, напор и дисциплина", "action and control, drive and discipline"),
        "harmony": ("дисциплинированная воля, выносливость и способность к долгому упорному труду", "disciplined will, endurance and the capacity for long, persistent effort"),
        "tension": ("подавленный гнев, фрустрация и действие через силу; то блок энергии, то срыв", "suppressed anger, frustration and forced action; now a block of energy, now an outburst"),
    },
    ("Jupiter", "Saturn"): {
        "theme": ("рост и ограничение, оптимизм и реализм", "growth and limitation, optimism and realism"),
        "harmony": ("баланс веры и расчёта: умение мечтать и строить долгосрочно и реалистично", "a balance of faith and calculation: the ability to dream and to build for the long term, realistically"),
        "tension": ("качели между «расширить» и «сжать», между риском и осторожностью — то избыток, то нехватка", "a swing between “expand” and “contract”, between risk and caution — now excess, now lack"),
    },
}


def _aspect_pair(p1: str, p2: str):
    if p1 in _PAIR_ORDER and p2 in _PAIR_ORDER:
        a, b = sorted((p1, p2), key=_PAIR_ORDER.index)
        return ASPECT_PAIR.get((a, b))
    return None


# --------------------------------------------------------------------------- #
#  Синастрия для пары: понятный язык
# --------------------------------------------------------------------------- #
# Важность планет для отношений (что сильнее «делает» совместимость).
_SYN_WEIGHT = {
    "Sun": 5, "Moon": 5, "Venus": 5, "Mars": 4, "Ascendant": 4, "Descendant": 4,
    "Mercury": 3, "Saturn": 3, "Medium_Coeli": 2, "Imum_Coeli": 2, "Jupiter": 2,
    "Uranus": 1, "Neptune": 1, "Pluto": 1,
}


def synastry_pair_text(p1: str, p2: str, nature: str, lang: str = "ru") -> Optional[str]:
    """Что означает контакт двух планет в паре — простым языком, по характеру аспекта."""
    pair = _aspect_pair(p1, p2)
    if not pair:
        return None
    if nature == "tense":
        return g(pair["tension"], lang)
    # гармоничные И соединение (нейтральное) трактуем как сильную сторону связи
    return g(pair["harmony"], lang)


def synastry_weight(p1: str, p2: str, orbit: float) -> float:
    """Вес контакта: важность планет + точность (узкий орб = сильнее)."""
    base = _SYN_WEIGHT.get(p1, 1) + _SYN_WEIGHT.get(p2, 1)
    return base + max(0.0, 3.0 - float(orbit))


def synastry_verdict(n_strength: int, n_challenge: int, score_desc: str, lang: str = "ru") -> str:
    """Вердикт по паре простым языком — из баланса сильных сторон и зон роста."""
    if n_strength >= 2 * max(1, n_challenge):
        tone = ("Между вами много естественного тепла и притяжения — это сильная, "
                "поддерживающая связь, где легко быть собой.",
                "There is a lot of natural warmth and attraction between you — a strong, "
                "supportive bond where it is easy to be yourselves.")
    elif n_challenge > n_strength:
        tone = ("Ваша пара — непростая, но живая и развивающая: сильное притяжение здесь "
                "соседствует с зонами напряжения, которые требуют внимания и работы.",
                "Yours is a challenging but alive and growth-oriented pair: strong attraction "
                "here sits next to zones of tension that need attention and work.")
    else:
        tone = ("У вас сбалансированная связь: есть и крепкие опоры, и точки роста — "
                "многое зависит от того, как вы оба готовы слышать друг друга.",
                "You have a balanced connection: both solid foundations and growth points — "
                "much depends on how willing you both are to hear each other.")
    tail = ("Ниже — на чём держится ваша близость и над чем стоит поработать.",
            "Below are what your closeness rests on and what is worth working on.")
    return f"{g(tone, lang)} {g(tail, lang)}"


# --- Разбор по сферам отношений ---
_SYN_ROMANTIC = {"Sun", "Moon", "Venus", "Mars", "Ascendant", "Descendant"}


def synastry_sphere_of(p1: str, p2: str):
    """Каким сферам отношений принадлежит контакт двух планет (может быть несколько)."""
    s = {p1, p2}
    spheres = []
    if ("Venus" in s or "Mars" in s) and s <= _SYN_ROMANTIC:
        spheres.append("passion")
    if "Moon" in s and s <= (_SYN_ROMANTIC | {"Saturn", "Jupiter"}):
        spheres.append("emotional")
    if "Mercury" in s:
        spheres.append("communication")
    if "Saturn" in s or (p1 == "Sun" and p2 == "Sun"):
        spheres.append("stability")
    return spheres


SYN_SPHERE_LABEL = {
    "passion": ("Притяжение и страсть", "Attraction and passion"),
    "emotional": ("Эмоциональная близость", "Emotional closeness"),
    "communication": ("Общение и понимание", "Communication and understanding"),
    "stability": ("Доверие и долгосрочность", "Trust and the long term"),
}
SYN_SPHERE_TEXT = {
    "passion": {
        "good": ("Между вами сильное взаимное притяжение — есть «химия», страсть и желание быть ближе.",
                 "There is strong mutual attraction between you — chemistry, passion and the wish to be closer."),
        "mixed": ("Притяжение есть, но переменчивое: страсть то вспыхивает, то наталкивается на разногласия и ревность.",
                  "There is attraction, but it fluctuates: passion flares up and then meets disagreements and jealousy."),
        "challenging": ("В сфере страсти есть трение: влечение неустойчиво и легко переходит в борьбу — над близостью стоит работать.",
                        "There is friction in the realm of passion: desire is unstable and easily turns into struggle — closeness needs work."),
        "quiet": ("Ярких контактов Венеры и Марса немного — страсть здесь спокойная, держится скорее на других опорах.",
                  "There are few strong Venus–Mars contacts — passion here is calm and rests more on other foundations."),
    },
    "emotional": {
        "good": ("Вы эмоционально настроены друг на друга: чувствуете настроение партнёра и легко создаёте ощущение дома.",
                 "You are emotionally attuned: you sense each other's moods and easily create a feeling of home."),
        "mixed": ("Эмоциональная связь живая, но требует внимания — вы по-разному переживаете и порой задеваете чувства друг друга.",
                  "The emotional bond is alive but needs attention — you feel things differently and sometimes hurt each other."),
        "challenging": ("В эмоциях есть дистанция или трение: бывает трудно почувствовать поддержку и безопасность рядом.",
                        "There is distance or friction in the emotional sphere: it can be hard to feel support and safety together."),
        "quiet": ("Сильных лунных контактов мало — эмоциональный фон ровный, без ярко выраженной взаимной чувствительности.",
                  "There are few strong Moon contacts — the emotional background is even, without pronounced mutual sensitivity."),
    },
    "communication": {
        "good": ("Вам легко общаться и понимать друг друга — разговоры даются естественно, есть общий язык.",
                 "You communicate and understand each other easily — conversation flows and you share a common language."),
        "mixed": ("Общение рабочее, но местами вы говорите на разных языках — важно уточнять и переспрашивать.",
                  "Communication works, but at times you speak different languages — it helps to clarify and ask again."),
        "challenging": ("В общении бывают сбои и споры: легко не понять друг друга, поэтому важно учиться договариваться.",
                        "There are glitches and arguments in communication: it is easy to misunderstand, so learning to negotiate matters."),
        "quiet": ("Контактов Меркурия немного — интеллектуальная связь нейтральна и не выходит на первый план.",
                  "There are few Mercury contacts — the intellectual link is neutral and not in the foreground."),
    },
    "stability": {
        "good": ("У пары крепкая основа для долгого союза: есть зрелость, надёжность и готовность к обязательствам.",
                 "The couple has a solid basis for a lasting union: maturity, reliability and readiness to commit."),
        "mixed": ("Долгосрочность реальна, но требует усилий: вопросы ответственности и свободы нужно согласовывать.",
                  "A long-term bond is realistic but takes effort: questions of responsibility and freedom need to be agreed."),
        "challenging": ("Для устойчивости связи нужна работа: возможны ограничения, контроль или прохлада, которые стоит смягчать.",
                        "Stability needs work: there may be limitation, control or coolness that are worth softening."),
        "quiet": ("Контактов Сатурна немного — союз гибкий и лёгкий, но опору на долгий срок придётся выстраивать сознательно.",
                  "There are few Saturn contacts — the union is flexible and light, but a long-term foundation must be built consciously."),
    },
}


def synastry_sphere_text(sphere: str, tone: str, lang: str = "ru") -> str:
    return g(SYN_SPHERE_TEXT.get(sphere, {}).get(tone, ("", "")), lang)


def synastry_sphere_label(sphere: str, lang: str = "ru") -> str:
    return g(SYN_SPHERE_LABEL.get(sphere, ("", "")), lang)


# --- Советы паре по сферам (для зон роста) ---
SYN_SPHERE_ADVICE = {
    "passion": ("Поддерживайте новизну и телесную близость, не давайте страсти раствориться в быту; прямо и без стеснения говорите о желаниях.",
                "Keep novelty and physical closeness alive, don't let passion dissolve into routine; talk about desires openly and without embarrassment."),
    "emotional": ("Находите время на простое тепло и разговоры о чувствах; учитесь замечать и называть эмоции друг друга, а не догадываться.",
                  "Make time for simple warmth and talks about feelings; learn to notice and name each other's emotions instead of guessing."),
    "communication": ("Проговаривайте важное прямо и переспрашивайте, не додумывая за партнёра; договоритесь, как вести спор, не раня друг друга.",
                      "Say important things directly and check understanding instead of assuming; agree on how to argue without hurting each other."),
    "stability": ("Согласуйте ожидания об обязательствах и свободе; стройте общее будущее маленькими реальными шагами, а не обещаниями.",
                  "Align your expectations about commitment and freedom; build a shared future in small real steps rather than promises."),
}


def synastry_sphere_advice(sphere: str, lang: str = "ru") -> str:
    return g(SYN_SPHERE_ADVICE.get(sphere, ("", "")), lang)


# --------------------------------------------------------------------------- #
#  Накладки домов: чья планета в каком доме партнёра
# --------------------------------------------------------------------------- #
_OVERLAY_PLANET = {
    "Sun": ("тепло, личность и жизненную силу", "warmth, identity and vitality"),
    "Moon": ("чувства, заботу и эмоциональную близость", "feelings, care and emotional closeness"),
    "Mercury": ("общение, идеи и обмен мыслями", "communication, ideas and an exchange of thoughts"),
    "Venus": ("нежность, любовь и удовольствие", "tenderness, love and pleasure"),
    "Mars": ("страсть, энергию и инициативу", "passion, energy and initiative"),
    "Jupiter": ("щедрость, оптимизм и рост", "generosity, optimism and growth"),
    "Saturn": ("серьёзность, опору и чувство долга", "seriousness, support and a sense of duty"),
}
_OVERLAY_HOUSE = {
    1: ("личность и самоощущение", "personality and sense of self"),
    2: ("деньги, ценности и чувство стабильности", "money, values and the sense of stability"),
    3: ("повседневное общение и быт", "everyday communication and routine"),
    4: ("дом, семью и корни", "home, family and roots"),
    5: ("романтику, флирт и творчество", "romance, flirtation and creativity"),
    6: ("быт, заботу и здоровье", "daily life, care and health"),
    7: ("партнёрство и брак", "partnership and marriage"),
    8: ("близость, страсть и общие ресурсы", "intimacy, passion and shared resources"),
    9: ("взгляды, путешествия и развитие", "worldview, travel and growth"),
    10: ("статус, цели и репутацию", "status, goals and reputation"),
    11: ("дружбу, планы и общее будущее", "friendship, plans and a shared future"),
    12: ("тайны, подсознание и уединение", "secrets, the unconscious and solitude"),
}


def synastry_overlay_text(planet_name: str, house_num: int, who: str, partner: str, lang: str = "ru") -> Optional[str]:
    pl = _OVERLAY_PLANET.get(planet_name)
    ho = _OVERLAY_HOUSE.get(house_num)
    if not pl or not ho:
        return None
    if lang == "en":
        return f"{who} brings {g(pl, lang)} into {partner}'s area of {g(ho, lang)}."
    return f"{who} привносит {g(pl, lang)} в сферу «{g(ho, lang)}» партнёра ({partner})."


# --------------------------------------------------------------------------- #
#  Композитная карта (карта самих отношений)
# --------------------------------------------------------------------------- #
_COMPOSITE_INTRO = {
    "Sun": ("Суть и цель ваших отношений", "The essence and purpose of your relationship"),
    "Moon": ("Эмоциональная атмосфера пары", "The emotional atmosphere of the couple"),
    "Mercury": ("Как вы общаетесь как пара", "How you communicate as a couple"),
    "Venus": ("Как вы любите и наслаждаетесь вместе", "How you love and enjoy life together"),
    "Mars": ("Как вы действуете и спорите вместе", "How you act and argue together"),
    "Ascendant": ("Образ вашей пары со стороны", "How your couple appears to others"),
    "Medium_Coeli": ("Общие цели и место пары в мире", "Shared goals and the couple's place in the world"),
}


def synastry_composite_text(point_name: str, sign: str, lang: str = "ru") -> Optional[str]:
    intro = _COMPOSITE_INTRO.get(point_name)
    arch = SIGN_ARCHETYPE.get(sign)
    if not intro or not arch:
        return None
    sign_ru = C.sign_name(sign, lang)
    if lang == "en":
        return f"{g(intro, lang)} — in {sign_ru}: {g(arch['essence'], lang)}. At its best — {g(arch['light'], lang)}."
    return f"{g(intro, lang)} — в знаке {sign_ru}: {g(arch['essence'], lang)}. В лучшем — {g(arch['light'], lang)}."


# --------------------------------------------------------------------------- #
#  Эссенциальные достоинства
# --------------------------------------------------------------------------- #
_OPP = {"Ari": "Lib", "Tau": "Sco", "Gem": "Sag", "Can": "Cap", "Leo": "Aqu", "Vir": "Pis",
        "Lib": "Ari", "Sco": "Tau", "Sag": "Gem", "Cap": "Can", "Aqu": "Leo", "Pis": "Vir"}
_DOMICILE = {
    "Sun": ["Leo"], "Moon": ["Can"], "Mercury": ["Gem", "Vir"], "Venus": ["Tau", "Lib"],
    "Mars": ["Ari", "Sco"], "Jupiter": ["Sag", "Pis"], "Saturn": ["Cap", "Aqu"],
}
_EXALT = {"Sun": "Ari", "Moon": "Tau", "Mercury": "Vir", "Venus": "Pis",
          "Mars": "Cap", "Jupiter": "Can", "Saturn": "Lib"}

DIGNITY_LABEL = {
    "domicile": ("обитель", "domicile"),
    "exaltation": ("экзальтация", "exaltation"),
    "detriment": ("изгнание", "detriment"),
    "fall": ("падение", "fall"),
}
DIGNITY_NOTE = {
    "domicile": ("планета в своём знаке — сильна и действует свободно, в естественной среде", "the planet is in its own sign — strong and acting freely, in its natural environment"),
    "exaltation": ("планета возвышена — её лучшие качества выражены особенно ярко", "the planet is exalted — its best qualities are expressed especially vividly"),
    "detriment": ("планете здесь некомфортно — функция работает с усилием и через компенсации", "the planet is uncomfortable here — the function works with effort and through compensation"),
    "fall": ("функция ослаблена и требует осознанной проработки", "the function is weakened and requires conscious work"),
}


def dignity_code(planet_name: str, sign: str) -> str:
    if planet_name in _DOMICILE and sign in _DOMICILE[planet_name]:
        return "domicile"
    if planet_name in _EXALT and _EXALT[planet_name] == sign:
        return "exaltation"
    if planet_name in _DOMICILE and sign in [_OPP[s] for s in _DOMICILE[planet_name]]:
        return "detriment"
    if planet_name in _EXALT and sign == _OPP[_EXALT[planet_name]]:
        return "fall"
    return ""


def dignity(planet_name: str, sign: str, lang: str = "ru") -> str:
    """Локализованная подпись достоинства (для отображения)."""
    code = dignity_code(planet_name, sign)
    return g(DIGNITY_LABEL[code], lang) if code else ""


# --------------------------------------------------------------------------- #
#  Трактовки отдельных факторов
# --------------------------------------------------------------------------- #
def interpret_sign(planet_name: str, sign: str, lang: str = "ru") -> str:
    role = PLANET_ROLE.get(planet_name)
    facets = SIGN_FACETS.get(sign)
    if not role or not facets:
        return ""
    planet_ru = C.point_name(planet_name, lang)
    sign_ru = C.sign_name(sign, lang)
    code = dignity_code(planet_name, sign)
    if lang == "en":
        text = (
            f"{planet_ru} is {g(role, lang)}. In {sign_ru} this function expresses itself "
            f"{g(facets['manner'], lang)}. The main motive is {g(facets['motive'], lang)}. "
            f"The growth area is {g(facets['shadow'], lang)}."
        )
        if code:
            text += f" Here {planet_ru} is in “{g(DIGNITY_LABEL[code], lang)}”: {g(DIGNITY_NOTE[code], lang)}."
    else:
        text = (
            f"{planet_ru} — это {g(role, lang)}. В знаке {sign_ru} эта функция проявляется "
            f"{g(facets['manner'], lang)}. Главный мотив — {g(facets['motive'], lang)}. "
            f"Точка роста — {g(facets['shadow'], lang)}."
        )
        if code:
            text += f" Здесь {planet_ru} в состоянии «{g(DIGNITY_LABEL[code], lang)}»: {g(DIGNITY_NOTE[code], lang)}."
    return text


# --------------------------------------------------------------------------- #
#  Развёрнутая трактовка планеты в знаке (как на тематических сайтах)
# --------------------------------------------------------------------------- #
SIGN_MODALITY = {
    "Ari": "cardinal", "Can": "cardinal", "Lib": "cardinal", "Cap": "cardinal",
    "Tau": "fixed", "Leo": "fixed", "Sco": "fixed", "Aqu": "fixed",
    "Gem": "mutable", "Vir": "mutable", "Sag": "mutable", "Pis": "mutable",
}
MODALITY_NAME = {
    "cardinal": ("кардинальный", "cardinal"),
    "fixed": ("фиксированный", "fixed"),
    "mutable": ("мутабельный", "mutable"),
}
ELEMENT_FLAVOR = {
    "fire": ("Стихия Огня придаёт этому импульсивность, азарт и прямоту — всё вспыхивает ярко и быстро, от души и на подъёме вдохновения.",
             "The element of Fire lends this impulsiveness, enthusiasm and directness — everything happens vividly, quickly and wholeheartedly, on a wave of inspiration."),
    "earth": ("Стихия Земли делает это практичным, основательным и нацеленным на результат — важны польза, надёжность и осязаемый итог, а не отвлечённые идеи.",
              "The element of Earth makes this practical, grounded and result-oriented — usefulness, reliability and a tangible outcome matter more than abstract ideas."),
    "air": ("Стихия Воздуха придаёт лёгкость, ум и общительность — всё проходит через мысль, слово и живой обмен с людьми.",
            "The element of Air lends lightness, intellect and sociability — everything goes through thought, word and exchange with others."),
    "water": ("Стихия Воды добавляет чувствительность, интуицию и глубину — всё пропускается через эмоции, переживания и тонкое внутреннее чутьё.",
              "The element of Water adds sensitivity, intuition and depth — everything is filtered through emotions, experiences and subtle inner perception."),
}
MODALITY_FLAVOR = {
    "cardinal": ("Кардинальное качество знака побуждает действовать, начинать и брать инициативу — человек стремится влиять на события, а не ждать у моря погоды.",
                 "The cardinal quality of the sign urges one to act, to start and to take initiative — the person seeks to influence events rather than wait."),
    "fixed": ("Фиксированное качество даёт устойчивость, верность и умение доводить начатое до конца — но и упрямство, сопротивление любым переменам.",
              "The fixed quality gives steadiness, loyalty and follow-through, but also resistance to change and stubbornness."),
    "mutable": ("Мутабельное качество делает проявление гибким, изменчивым и приспособляемым — легко перестраиваться, но труднее держать одну линию.",
                "The mutable quality makes the expression flexible, changeable and adaptable — easy to adjust, but harder to hold one line."),
}
ADVICE_BY_SIGN = {
    "Ari": ("Учитесь терпению и доводите начатое до конца, считаясь с интересами других.",
            "Learn patience and to finish what you start, taking others' interests into account."),
    "Tau": ("Позволяйте себе перемены и не цепляйтесь за привычное лишь из страха потерять устойчивость.",
            "Allow yourself change, and don't cling to the familiar merely out of fear of losing stability."),
    "Gem": ("Углубляйтесь в выбранное и доводите дела до конца, не распыляясь на всё сразу.",
            "Go deeper into what you choose and finish things, without scattering yourself across everything at once."),
    "Can": ("Учитесь отпускать прошлое и не уходить в обиду; берегите личные границы.",
            "Learn to let go of the past and not retreat into hurt; protect your personal boundaries."),
    "Leo": ("Цените себя независимо от чужого признания и умейте делить внимание с другими.",
            "Value yourself regardless of outside recognition, and learn to share the spotlight."),
    "Vir": ("Смягчайте самокритику и принимайте несовершенство — и своё, и чужое.",
            "Ease the self-criticism and accept imperfection — both your own and others'."),
    "Lib": ("Учитесь принимать решения и отстаивать своё мнение, не боясь конфликта.",
            "Learn to make decisions and stand by your opinion without fearing conflict."),
    "Sco": ("Отпускайте контроль и учитесь доверять; направляйте свою силу в созидание, а не в борьбу.",
            "Release control and learn to trust; channel intensity into creation rather than struggle."),
    "Sag": ("Будьте внимательнее к деталям и обязательствам; меньше поучайте, больше слушайте.",
            "Pay closer attention to details and commitments; preach less, listen more."),
    "Cap": ("Позволяйте себе чувства и отдых — не всё в жизни измеряется результатом и статусом.",
            "Allow yourself feelings and rest — not everything in life is measured by results and status."),
    "Aqu": ("Не отгораживайтесь от близких; соединяйте оригинальные идеи с теплом и участием.",
            "Don't wall yourself off from loved ones; combine original ideas with warmth and involvement."),
    "Pis": ("Укрепляйте границы и опору в реальности; помогая другим, не растворяйтесь в них.",
            "Strengthen your boundaries and footing in reality; in helping others, don't dissolve into them."),
}

# Доменная модель планеты: что именно она «делает», и как проявляется в любви и в деле.
# Используется, чтобы развёрнутая трактовка раскрывала сферы жизни (как у авторских статей).
PLANET_SPHERES = {
    "Sun": {
        "function": ("как вы проявляете своё «я», чего по-настоящему хотите и как утверждаете волю и достоинство",
                     "how you express your “self”, what you truly want and how you assert your will and dignity"),
        "love": ("В любви Солнце показывает, как вы дарите тепло и щедрость, гордитесь избранником и хотите чувствовать себя для него особенным",
                 "In love the Sun shows how you give warmth and generosity, take pride in your partner and want to feel special to them"),
        "work": ("В деле Солнце отвечает за стремление к признанию, творческой самореализации и роли, которой можно гордиться",
                 "In work the Sun governs the drive for recognition, creative self-realization and a role to be proud of"),
    },
    "Moon": {
        "function": ("как вы чувствуете, реагируете, заботитесь и что вам нужно для душевного покоя",
                     "how you feel, react, care and what you need for inner peace"),
        "love": ("В любви Луна показывает потребность в эмоциональной близости, заботе и ощущении дома рядом с партнёром",
                 "In love the Moon shows the need for emotional closeness, care and a sense of home beside your partner"),
        "work": ("В деле Луна тянет к занятиям, где есть забота о людях, уют, дом, питание, публика или работа с настроением и памятью",
                 "In work the Moon draws you to caring for people, comfort, home, food, the public, or work with mood and memory"),
    },
    "Mercury": {
        "function": ("как вы думаете, учитесь, запоминаете, принимаете решения и говорите",
                     "how you think, learn, remember, make decisions and speak"),
        "love": ("В отношениях Меркурий показывает, что близость рождается через общение: вам важно, чтобы партнёра было интересно слушать и чтобы вас понимали с полуслова",
                 "In relationships Mercury shows that closeness is born through communication: it matters that your partner is interesting to listen to and that you are understood at half a word"),
        "work": ("В деле Меркурий благоприятствует занятиям со словом, информацией, анализом, обучением, торговлей и контактами",
                 "In work Mercury favours occupations with words, information, analysis, teaching, trade and contacts"),
    },
    "Venus": {
        "function": ("как вы любите, что цените, что считаете красивым и как притягиваете к себе",
                     "how you love, what you value, what you find beautiful and how you attract"),
        "love": ("Венера — сама способность любить и наслаждаться: она показывает ваш стиль ухаживания, что вас очаровывает и как вы дарите нежность",
                 "Venus is the very capacity to love and enjoy: it shows your style of courtship, what charms you and how you give tenderness"),
        "work": ("В деле Венера тянет к красоте, искусству, моде, дипломатии, деньгам и всему, что связано с гармонией и людьми",
                 "In work Venus draws you to beauty, art, fashion, diplomacy, money and all that involves harmony and people"),
    },
    "Mars": {
        "function": ("как вы действуете, желаете, злитесь и отстаиваете себя",
                     "how you act, desire, get angry and assert yourself"),
        "love": ("В любви Марс отвечает за страсть, влечение и инициативу: как вы добиваетесь желаемого и проявляете пыл",
                 "In love Mars governs passion, attraction and initiative: how you pursue what you want and show ardour"),
        "work": ("В деле Марс даёт энергию, напор и дух соперничества; вам подходят занятия, требующие воли, смелости и быстрого действия",
                 "In work Mars gives energy, drive and a competitive spirit; you suit work demanding will, courage and quick action"),
    },
    "Jupiter": {
        "function": ("во что вы верите, как растёте, рискуете и расширяете горизонты",
                     "what you believe in, how you grow, take risks and expand horizons"),
        "love": ("В отношениях Юпитер приносит щедрость, оптимизм и совместные горизонты; вам важны общий рост и вера в партнёра",
                 "In relationships Jupiter brings generosity, optimism and shared horizons; common growth and faith in your partner matter"),
        "work": ("В деле Юпитер благоприятствует обучению, праву, путешествиям, издательству и всему, где есть масштаб, смысл и развитие",
                 "In work Jupiter favours teaching, law, travel, publishing and anything with scale, meaning and growth"),
    },
    "Saturn": {
        "function": ("как вы выстраиваете опору, держите дисциплину и принимаете ответственность",
                     "how you build a foundation, keep discipline and take responsibility"),
        "love": ("В любви Сатурн означает верность, серьёзность и зрелость: чувства проверяются временем, а отношения — это обязательство",
                 "In love Saturn means loyalty, seriousness and maturity: feelings are tested by time and a relationship is a commitment"),
        "work": ("В деле Сатурн даёт дисциплину, выносливость и умение строить надолго; успех приходит через упорство, опыт и ответственность",
                 "In work Saturn gives discipline, endurance and the ability to build for the long term; success comes through persistence and experience"),
    },
    "Uranus": {
        "function": ("где вам нужна свобода, что вы хотите изменить и в чём проявляете оригинальность",
                     "where you need freedom, what you want to change and how you show originality"),
        "love": ("В отношениях Уран ценит свободу, равенство и нестандартность; ему тесно в рамках и шаблонах",
                 "In relationships Uranus values freedom, equality and unconventionality; it feels cramped by frames and templates"),
        "work": ("В деле Уран тянет к технологиям, науке, реформам и всему новаторскому, где можно ломать шаблоны",
                 "In work Uranus draws you to technology, science, reform and everything innovative where templates can be broken"),
    },
    "Neptune": {
        "function": ("как вы мечтаете, сострадаете, вдохновляетесь и где растворяете границы",
                     "how you dream, feel compassion, get inspired and where you dissolve boundaries"),
        "love": ("В любви Нептун несёт идеализацию, романтику и слияние душ; есть риск принять мечту за реальность",
                 "In love Neptune brings idealization, romance and a merging of souls; there is a risk of mistaking the dream for reality"),
        "work": ("В деле Нептун благоприятствует искусству, музыке, кино, помощи, духовным и целительским практикам",
                 "In work Neptune favours art, music, film, helping, spiritual and healing practices"),
    },
    "Pluto": {
        "function": ("как вы трансформируетесь, что переживаете глубоко и где ищете власть и контроль",
                     "how you transform, what you experience deeply and where you seek power and control"),
        "love": ("В любви Плутон несёт глубину, страсть и преображение; чувства всепоглощающи, а близость — на грани слияния и власти",
                 "In love Pluto brings depth, passion and transformation; feelings are all-consuming and closeness borders on merging and power"),
        "work": ("В деле Плутон тянет к исследованию, психологии, кризис-менеджменту, финансам и всему, что связано с глубинными процессами",
                 "In work Pluto draws you to research, psychology, crisis management, finance and all that involves deep processes"),
    },
}
# Тон любви и работы по стихии знака — чтобы сферы окрашивались знаком, а не повторяли общее.
EL_LOVE_TONE = {
    "fire": ("Любит ярко, страстно и порывисто — быстро загорается и нуждается в свободе, азарте и восхищении.",
             "Loves brightly, passionately and proactively — ignites quickly and needs freedom, excitement and admiration."),
    "earth": ("Любит надёжно, телесно и верно — ценит стабильность, заботу делом и осязаемые знаки внимания.",
              "Loves reliably, physically and faithfully — values stability, care through deeds and tangible signs of affection."),
    "air": ("Любит через общение, лёгкость и общие интересы — нуждается в диалоге, свободе и личном пространстве.",
            "Loves through communication, lightness and shared interests — needs dialogue, freedom and personal space."),
    "water": ("Любит глубоко, чутко и преданно — нуждается в эмоциональной близости, заботе и душевном слиянии.",
              "Loves deeply, sensitively and devotedly — needs emotional closeness, care and a soulful merging."),
}
EL_WORK_TONE = {
    "fire": ("В работе нужны азарт, инициатива и быстрый результат — рутина гасит интерес.",
             "At work needs drive, initiative and quick results; routine dims the interest."),
    "earth": ("В работе ценит порядок, надёжность и осязаемую пользу, готов к долгому методичному труду.",
              "At work values order, reliability and tangible benefit, ready for long methodical effort."),
    "air": ("В работе важны общение, идеи и разнообразие задач; ценит интеллектуальную свободу.",
            "At work communication, ideas and variety matter; values intellectual freedom."),
    "water": ("В работе важны атмосфера, доверие и смысл; лучше всего работает там, где можно опереться на интуицию и чувства.",
              "At work atmosphere, trust and meaning matter; works well where intuition and feeling can be trusted."),
}


# --------------------------------------------------------------------------- #
#  Авторские тексты «планета в знаке»: уникальный разбор под конкретную
#  комбинацию (в отличие от композиционной сборки из кубиков ниже).
#  Пилот: Солнце и Луна × 12 знаков, RU. Пустой en ("") → откат к композиции.
#  Редактируются через админку (content_store, namespace AUTHORED_SIGN).
# --------------------------------------------------------------------------- #
# Авторские тексты - закрытый контент-слой (НЕ под AGPL, в репозиторий не входит).
# Хранятся в data/authored_content.json; без файла - откат к композиционным трактовкам.
def _load_authored():
    import json as _json
    from pathlib import Path as _P
    f = _P(__file__).resolve().parent.parent / "data" / "authored_content.json"
    try:
        raw = _json.loads(f.read_text(encoding="utf-8"))
    except Exception:
        return {}, {}, {}, {}
    return (
        {(p, s): (ru, en) for p, s, ru, en in raw.get("sign", [])},
        {(p, int(h)): (ru, en) for p, h, ru, en in raw.get("house", [])},
        {(a, b, asp): (ru, en) for a, b, asp, ru, en in raw.get("aspect", [])},
        {(p, ang, asp): (ru, en) for p, ang, asp, ru, en in raw.get("angle", [])},
    )


AUTHORED_SIGN, AUTHORED_HOUSE, AUTHORED_ASPECT, AUTHORED_ANGLE = _load_authored()


def authored_sign(planet_name: str, sign: str, lang: str = "ru") -> str:
    """Авторский разбор комбинации, если он есть для нужного языка (иначе '')."""
    pair = AUTHORED_SIGN.get((planet_name, sign))
    if not pair:
        return ""
    return g(pair, lang) or ""


# --------------------------------------------------------------------------- #
#  Авторские тексты «планета в доме» (RU+EN). Ключ — (planet, house_num: 1..12).
#  Наполняется скриптом-мёржем; редактируется через админку (namespace AUTHORED_HOUSE).
# --------------------------------------------------------------------------- #
# AUTHORED_HOUSE: закрытый слой, см. _load_authored() выше.


def authored_house(planet_name: str, house_num, lang: str = "ru") -> str:
    """Авторский разбор «планета в доме», если есть для нужного языка (иначе '')."""
    if not house_num:
        return ""
    pair = AUTHORED_HOUSE.get((planet_name, int(house_num)))
    if not pair:
        return ""
    return g(pair, lang) or ""


# --------------------------------------------------------------------------- #
#  Авторские тексты аспектов (пара планет × тип аспекта), RU+EN.
#  Ключ — (p1, p2, aspect) в каноническом порядке _PAIR_ORDER; aspect — англ. lowercase.
#  Наполняется скриптом-мёржем; редактируется через админку (namespace AUTHORED_ASPECT).
# --------------------------------------------------------------------------- #
# AUTHORED_ASPECT: закрытый слой, см. _load_authored() выше.


# Порядок для авторских аспектов — шире, чем _PAIR_ORDER (композиция ASPECT_PAIR):
# добавлены высшие планеты, чтобы канонизировать пары вида (классическая, высшая).
_ASPECT_ORDER = _PAIR_ORDER + ["Uranus", "Neptune", "Pluto"]


def authored_aspect(p1: str, p2: str, aspect: str, lang: str = "ru") -> str:
    """Авторский текст аспекта пары планет, если есть для нужного языка (иначе '')."""
    if p1 in _ASPECT_ORDER and p2 in _ASPECT_ORDER:
        a, b = sorted((p1, p2), key=_ASPECT_ORDER.index)
        found = AUTHORED_ASPECT.get((a, b, aspect))
        if found:
            return g(found, lang) or ""
    return ""


def interpret_sign_full(planet_name: str, sign: str, lang: str = "ru", retro: bool = False) -> list:
    """Развёрнутая трактовка планеты в знаке: список разделов [{label, text}]."""
    role = PLANET_ROLE.get(planet_name)
    facets = SIGN_FACETS.get(sign)
    arch = SIGN_ARCHETYPE.get(sign)
    if not role or not facets or not arch:
        return []
    planet_ru = C.point_name(planet_name, lang)
    sign_ru = C.sign_name(sign, lang)
    el_code = C.SIGNS.get(sign, {}).get("element", "")
    el_name = C.sign_element(sign, lang)
    mod_code = SIGN_MODALITY.get(sign, "")
    mod_name = g(MODALITY_NAME.get(mod_code, ("", "")), lang)
    el_flavor = g(ELEMENT_FLAVOR.get(el_code, ("", "")), lang)
    mod_flavor = g(MODALITY_FLAVOR.get(mod_code, ("", "")), lang)
    code = dignity_code(planet_name, sign)
    dig_note = g(DIGNITY_NOTE[code], lang) if code else ""
    advice = g(ADVICE_BY_SIGN.get(sign, ("", "")), lang)
    manner = g(facets["manner"], lang)
    motive = g(facets["motive"], lang)
    sh_facet = g(facets["shadow"], lang)
    light = g(arch["light"], lang)
    sh_arch = g(arch["shadow"], lang)
    spheres = PLANET_SPHERES.get(planet_name)
    el_love = g(EL_LOVE_TONE.get(el_code, ("", "")), lang)
    el_work = g(EL_WORK_TONE.get(el_code, ("", "")), lang)
    authored = authored_sign(planet_name, sign, lang)

    L = (lambda ru, en: en if lang == "en" else ru)
    blocks = []

    if lang == "en":
        blocks.append({"label": "Essence", "text":
            f"{planet_ru} stands for {g(role, lang)}. In {sign_ru} — an {el_name}, {mod_name} sign with the archetype "
            f"“{g(arch['archetype'], lang)}” ({g(arch['essence'], lang)}) — this function takes on its colours."})
        if spheres:
            blocks.append({"label": f"How {planet_ru} works", "text":
                f"{planet_ru} here defines {g(spheres['function'], lang)}. {el_flavor} In practice it acts {manner}."})
        blocks.append({"label": "Character and motivation", "text":
            f"The main inner driver is {motive}. {mod_flavor}"})
        blocks.append({"label": "Strengths", "text":
            f"At its best this gives {light} — these are the natural strengths of the placement."})
        blocks.append({"label": "Weaknesses and shadow", "text":
            f"The vulnerable side is {sh_facet}; in the shadow it can deepen into {sh_arch}. This is the growth zone."})
        if spheres:
            blocks.append({"label": "Love and closeness", "text": f"{g(spheres['love'], lang)}. {el_love}"})
            blocks.append({"label": "Work and vocation", "text": f"{g(spheres['work'], lang)}. {el_work}"})
        if retro and planet_name in RETROGRADE_NOTE:
            blocks.append({"label": "Retrograde", "text": retrograde_note(planet_name, lang)})
        blocks.append({"label": "Advice", "text": f"{dig_note + '. ' if dig_note else ''}{advice}"})
    else:
        blocks.append({"label": "Суть", "text":
            f"{planet_ru} символизирует {g(role, lang)}. В знаке {sign_ru} — это {el_name.lower()}, {mod_name} знак с архетипом "
            f"«{g(arch['archetype'], lang)}» ({g(arch['essence'], lang)}) — и эта функция окрашивается соответствующим образом."})
        if spheres:
            blocks.append({"label": f"Как работает {planet_ru}", "text":
                f"{planet_ru} здесь определяет то, {g(spheres['function'], lang)}. {el_flavor} На практике это происходит {manner}."})
        blocks.append({"label": "Характер и мотивация", "text":
            f"Главный внутренний двигатель — {motive}. {mod_flavor}"})
        blocks.append({"label": "Сильные стороны", "text":
            f"В лучших проявлениях это даёт {light} — таковы природные сильные стороны положения."})
        blocks.append({"label": "Слабые стороны и тень", "text":
            f"Уязвимая сторона — {sh_facet}; в тени это углубляется до состояния «{sh_arch}». Это и есть зона роста."})
        if spheres:
            blocks.append({"label": "Любовь и близость", "text": f"{g(spheres['love'], lang)}. {el_love}"})
            blocks.append({"label": "Работа и призвание", "text": f"{g(spheres['work'], lang)}. {el_work}"})
        if retro and planet_name in RETROGRADE_NOTE:
            blocks.append({"label": "Ретроградность", "text": retrograde_note(planet_name, lang)})
        blocks.append({"label": "Совет", "text": f"{dig_note + '. ' if dig_note else ''}{advice}"})

    # Авторский разбор (если написан для этой комбинации) — ведущим блоком,
    # композиционные разделы остаются ниже как структурированная детализация.
    if authored:
        blocks.insert(0, {"label": L("Разбор", "Reading"), "text": authored})
    return blocks


# Опыт дома — что значит, когда планета делает эту область жизни важной (натальная рамка).
HOUSE_EXP = {
    1: ("Эта сфера всегда «на виду»: она окрашивает ваш характер, манеру держаться и первое впечатление о вас.",
        "This area is always on display: it colours your character, bearing and the first impression you make."),
    2: ("Здесь решаются вопросы денег, имущества, талантов и того, что даёт чувство устойчивости и собственной ценности.",
        "Here lie money, possessions, talents and whatever gives a sense of stability and self-worth."),
    3: ("Это территория повседневности: общение, учёба, короткие поездки, контакты с роднёй, соседями и ближним кругом.",
        "This is the realm of the everyday: communication, learning, short trips, contact with relatives, neighbours and the close circle."),
    4: ("Корни всего — дом, семья, родители и прошлое; здесь вы ищете опору и ощущение «своего места».",
        "The roots of everything — home, family, parents and the past; here you seek support and a sense of belonging."),
    5: ("Это территория радости и самопроявления: творчество, влюблённости, удовольствия, дети и игра.",
        "This is the ground of joy and self-expression: creativity, romance, pleasures, children and play."),
    6: ("Будни и порядок: работа, режим, забота о здоровье и привычка приносить пользу.",
        "Daily life and order: work, routine, care for health and the habit of being useful."),
    7: ("Всё разворачивается через других: партнёрство, брак и близкие отношения «один на один».",
        "Everything unfolds through others: partnership, marriage and close one-to-one relationships."),
    8: ("Глубокие и сильные темы: близость, кризисы, чужие ресурсы, трансформация и то, что обычно скрыто.",
        "Deep and powerful themes: intimacy, crises, shared resources, transformation and what is usually hidden."),
    9: ("Тяга к большему: смысл, путешествия, обучение, мировоззрение и расширение горизонтов.",
        "A pull toward the larger: meaning, travel, learning, worldview and the broadening of horizons."),
    10: ("Вершина карты, видимая обществу: карьера, статус, призвание и репутация.",
         "The peak of the chart, visible to society: career, status, vocation and reputation."),
    11: ("Будущее и связи: друзья, единомышленники, цели, мечты и причастность к сообществам.",
         "The future and connections: friends, like-minded people, goals, dreams and belonging to communities."),
    12: ("Закулисье жизни: подсознание, уединение, тайны, сны и тема служения и отпускания.",
         "Life behind the scenes: the subconscious, solitude, secrets, dreams and the themes of service and letting go."),
}


def _ord_en(n: int) -> str:
    if 10 <= n % 100 <= 20:
        suf = "th"
    else:
        suf = {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return f"{n}{suf}"


# --------------------------------------------------------------------------- #
#  Простой режим: дома как сферы жизни + трактовки без терминов
# --------------------------------------------------------------------------- #
HOUSE_SPHERE = {
    1: ("личность и характер", "personality and character"),
    2: ("деньги и ценности", "money and values"),
    3: ("общение и учёба", "communication and learning"),
    4: ("дом и семья", "home and family"),
    5: ("творчество, любовь и дети", "creativity, romance and children"),
    6: ("работа и здоровье", "work and health"),
    7: ("партнёрство и брак", "partnership and marriage"),
    8: ("кризисы, близость и общие финансы", "crises, intimacy and shared finances"),
    9: ("кругозор, учёба и путешествия", "outlook, study and travel"),
    10: ("карьера и положение в обществе", "career and social standing"),
    11: ("друзья, цели и мечты", "friends, goals and dreams"),
    12: ("внутренний мир, уединение и тайны", "inner world, solitude and secrets"),
}
# Что планета «делает» — повседневным языком, без терминов.
PLAIN_PLANET = {
    "Sun": ("Солнце — это ваша личность, воля и то, чего вы по-настоящему хотите от жизни",
            "The Sun is your personality, will and what you truly want from life"),
    "Moon": ("Луна — это ваши чувства, привычки и потребность в тепле и заботе",
             "The Moon is your feelings, habits and need for warmth and care"),
    "Mercury": ("Меркурий — это то, как вы думаете, говорите и учитесь",
                "Mercury is how you think, speak and learn"),
    "Venus": ("Венера — это как вы любите, что вам нравится и что вы цените",
              "Venus is how you love, what you enjoy and what you value"),
    "Mars": ("Марс — это ваша энергия, желания и то, как вы добиваетесь своего",
             "Mars is your energy, desires and how you go after what you want"),
    "Jupiter": ("Юпитер — это ваш оптимизм, стремление расти и везение",
                "Jupiter is your optimism, drive to grow and luck"),
    "Saturn": ("Сатурн — это ваша дисциплина, ответственность и страхи",
               "Saturn is your discipline, responsibility and fears"),
    "Uranus": ("Уран — это ваша тяга к свободе, переменам и всему необычному",
               "Uranus is your urge for freedom, change and the unusual"),
    "Neptune": ("Нептун — это ваши мечты, интуиция и творческое воображение",
                "Neptune is your dreams, intuition and creative imagination"),
    "Pluto": ("Плутон — это ваша внутренняя сила и способность глубоко меняться",
              "Pluto is your inner strength and ability to deeply transform"),
}


def house_sphere(house_num, lang: str = "ru") -> str:
    return g(HOUSE_SPHERE.get(house_num, ("", "")), lang) if house_num else ""


def interpret_plain(planet_name: str, sign: str, house_num=None, lang: str = "ru") -> str:
    """Трактовка планеты в знаке/доме повседневным языком, без астро-терминов."""
    plain = PLAIN_PLANET.get(planet_name)
    facets = SIGN_FACETS.get(sign)
    if not plain or not facets:
        return ""
    manner = g(facets["manner"], lang)
    sphere = house_sphere(house_num, lang)
    if lang == "en":
        s = f"{g(plain, lang)}. Here it shows up {manner}."
        if sphere:
            s += f" This is most noticeable in the area of life: {sphere}."
        return s
    s = f"{g(plain, lang)}. Здесь это проявляется {manner}."
    if sphere:
        s += f" Сильнее всего это заметно в сфере жизни «{sphere}»."
    return s


# Описания стихий/крестов без терминов (для простого режима).
EL_PLAIN = {
    "fire": ("Здесь много энергии, азарта и прямоты — всё происходит ярко, быстро и от души.",
             "There is plenty of energy, drive and directness — everything happens vividly, quickly and wholeheartedly."),
    "earth": ("Этому свойственны практичность, надёжность и опора на результат, а не на отвлечённые идеи.",
              "This brings practicality, reliability and a focus on results rather than abstract ideas."),
    "air": ("Этому свойственны лёгкость, ум и общительность — всё проходит через мысль, слово и общение.",
            "This brings lightness, intellect and sociability — everything goes through thought, word and contact."),
    "water": ("Этому свойственны чувствительность, интуиция и глубина — всё пропускается через чувства.",
              "This brings sensitivity, intuition and depth — everything is filtered through feeling."),
}
MOD_PLAIN = {
    "cardinal": ("Есть тяга начинать, действовать первым и влиять на события.",
                 "There is a pull to start, to act first and to influence events."),
    "fixed": ("Есть устойчивость и упорство, но и сопротивление переменам.",
              "There is steadiness and persistence, but also resistance to change."),
    "mutable": ("Есть гибкость и умение приспосабливаться, но труднее держать одну линию.",
                "There is flexibility and adaptability, but it is harder to hold one line."),
}


def interpret_sign_full_plain(planet_name: str, sign: str, house_num=None,
                              lang: str = "ru", retro: bool = False) -> list:
    """Полный разбор планеты — теми же блоками, но повседневным языком, без терминов."""
    plain = PLAIN_PLANET.get(planet_name)
    facets = SIGN_FACETS.get(sign)
    arch = SIGN_ARCHETYPE.get(sign)
    if not plain or not facets or not arch:
        txt = interpret_plain(planet_name, sign, house_num, lang)
        return [{"label": "Суть" if lang != "en" else "Essence", "text": txt}] if txt else []
    manner = g(facets["manner"], lang)
    motive = g(facets["motive"], lang)
    sh_facet = g(facets["shadow"], lang)
    light = g(arch["light"], lang)
    sh_arch = g(arch["shadow"], lang)
    sign_ru = C.sign_name(sign, lang)
    el_code = C.SIGNS.get(sign, {}).get("element", "")
    spheres = PLANET_SPHERES.get(planet_name)
    el_p = g(EL_PLAIN.get(el_code, ("", "")), lang)
    mod_p = g(MOD_PLAIN.get(SIGN_MODALITY.get(sign, ""), ("", "")), lang)
    el_love = g(EL_LOVE_TONE.get(el_code, ("", "")), lang)
    el_work = g(EL_WORK_TONE.get(el_code, ("", "")), lang)
    advice = g(ADVICE_BY_SIGN.get(sign, ("", "")), lang)
    sphere = house_sphere(house_num, lang)
    L = (lambda ru, en: en if lang == "en" else ru)
    blocks = []
    if lang == "en":
        ess = f"{g(plain, lang)}. In {sign_ru} it shows up {manner}."
        if sphere:
            ess += f" It is most noticeable in the area of life: {sphere}."
        blocks.append({"label": "Essence", "text": ess})
        blocks.append({"label": "What drives it", "text": f"The main driver is {motive}. {el_p} {mod_p}"})
        blocks.append({"label": "Strengths", "text": f"At its best this gives {light}."})
        blocks.append({"label": "What to work on", "text": f"Harder areas: {sh_facet}; under stress it can show up as {sh_arch}."})
        if spheres:
            blocks.append({"label": "Love and closeness", "text": f"{g(spheres['love'], lang)}. {el_love}"})
            blocks.append({"label": "Work and vocation", "text": f"{g(spheres['work'], lang)}. {el_work}"})
        if retro:
            blocks.append({"label": "Unfolding over time", "text": "At birth this planet appeared to move “backward” — its force is turned more inward, toward rethinking, and unfolds gradually."})
        if advice:
            blocks.append({"label": "Advice", "text": advice})
        return blocks
    ess = f"{g(plain, lang)}. В знаке {sign_ru} это проявляется {manner}."
    if sphere:
        ess += f" Сильнее всего это заметно в сфере жизни «{sphere}»."
    blocks.append({"label": "Суть", "text": ess})
    blocks.append({"label": "Что движет", "text": f"Главное, что движет, — {motive}. {el_p} {mod_p}"})
    blocks.append({"label": "Сильные стороны", "text": f"В лучшем проявлении это даёт {light}."})
    blocks.append({"label": "Над чем поработать", "text": f"Труднее даётся {sh_facet}; в напряжении может проявляться как {sh_arch}."})
    if spheres:
        blocks.append({"label": "Любовь и близость", "text": f"{g(spheres['love'], lang)}. {el_love}"})
        blocks.append({"label": "Работа и призвание", "text": f"{g(spheres['work'], lang)}. {el_work}"})
    if retro:
        blocks.append({"label": "Развитие во времени", "text": "Эта планета при рождении двигалась как бы «вспять» — её сила направлена скорее внутрь, на переосмысление, и раскрывается не сразу."})
    if advice:
        blocks.append({"label": "Совет", "text": advice})
    return blocks


# Личностные черты по знаку обычными словами (для рассказа «Просто о себе»).
PLAIN_SIGN_TRAITS = {
    "Ari": ("энергичный, смелый, прямой и нетерпеливый", "energetic, bold, direct and impatient"),
    "Tau": ("спокойный, надёжный, практичный и любящий комфорт", "calm, reliable, practical and comfort-loving"),
    "Gem": ("любознательный, общительный, гибкий и непоседливый", "curious, sociable, flexible and restless"),
    "Can": ("чуткий, заботливый, привязчивый и ранимый", "sensitive, caring, attached and easily hurt"),
    "Leo": ("яркий, щедрый, гордый и любящий внимание", "bright, generous, proud and attention-loving"),
    "Vir": ("внимательный, аккуратный, практичный и самокритичный", "attentive, neat, practical and self-critical"),
    "Lib": ("обаятельный, дипломатичный, тянущийся к гармонии и нерешительный", "charming, diplomatic, harmony-seeking and indecisive"),
    "Sco": ("глубокий, страстный, проницательный и скрытный", "deep, passionate, perceptive and private"),
    "Sag": ("оптимистичный, свободолюбивый, искренний и любящий простор", "optimistic, freedom-loving, sincere and craving space"),
    "Cap": ("ответственный, целеустремлённый, сдержанный и упорный", "responsible, goal-driven, reserved and persistent"),
    "Aqu": ("независимый, оригинальный, дружелюбный и непредсказуемый", "independent, original, friendly and unpredictable"),
    "Pis": ("мечтательный, чуткий, сострадательный и творческий", "dreamy, sensitive, compassionate and creative"),
}
# Сфера жизни планеты обычными словами (для «противоречий и созвучий»).
PLANET_AREA = {
    "Sun": ("ваша воля и характер", "your will and character"),
    "Moon": ("ваши чувства и настроение", "your feelings and mood"),
    "Mercury": ("ваши мысли и слова", "your thoughts and words"),
    "Venus": ("любовь и то, что вам нравится", "love and what you enjoy"),
    "Mars": ("энергия и желания", "energy and desire"),
    "Jupiter": ("оптимизм и стремление расти", "optimism and the drive to grow"),
    "Saturn": ("дисциплина и страхи", "discipline and fears"),
    "Ascendant": ("то, как вы себя подаёте", "the way you present yourself"),
    "Medium_Coeli": ("карьера и цели", "career and goals"),
}


def _split_traits(text: str) -> list:
    """Разбить перечисление черт («надёжность, терпение и вкус к жизни») на пункты."""
    if not text:
        return []
    t = text
    for sep in (" и ", " and ", "; "):
        t = t.replace(sep, ", ")
    out = []
    for part in t.split(","):
        p = part.strip().strip(".")
        if p:
            out.append(p[0].upper() + p[1:])
    return out


def plain_story(signs: dict, aspects: list, lang: str = "ru") -> dict:
    """Тематический рассказ о человеке без терминов: разделы по сферам жизни + связи."""
    def fc(s, k):  # грань знака (manner/motive/shadow)
        return g(SIGN_FACETS[s][k], lang) if s in SIGN_FACETS else ""

    def ar(s, k):  # архетип знака (light/shadow/essence)
        return g(SIGN_ARCHETYPE[s][k], lang) if s in SIGN_ARCHETYPE else ""

    def tr(s):  # личностные черты
        return g(PLAIN_SIGN_TRAITS[s], lang) if s in PLAIN_SIGN_TRAITS else ""

    sun, moon, asc = signs.get("sun"), signs.get("moon"), signs.get("asc")
    me, ve, ma = signs.get("mercury"), signs.get("venus"), signs.get("mars")
    sat, mc, h2, h7 = signs.get("saturn"), signs.get("mc"), signs.get("h2"), signs.get("h7")
    L = (lambda ru, en: en if lang == "en" else ru)

    sections = []

    def add(title_ru, title_en, text):
        if text:
            sections.append({"title": L(title_ru, title_en), "text": text})

    if lang == "en":
        add("Character", "Character",
            f"By nature you are {tr(sun)}. Deep down you are driven by {fc(sun,'motive')}. "
            f"At first meeting you come across as {tr(asc)} — that is your “calling card”.")
        add("Feelings", "Feelings",
            f"In your feelings and moods you are {tr(moon)}. To feel calm and safe you need {fc(moon,'motive')}. "
            f"In hard moments, watch that this does not turn into {fc(moon,'shadow')}.")
        add("Mind", "Mind",
            f"You think, learn and communicate {fc(me,'manner')}. At best this gives {ar(me,'light')}, "
            f"but sometimes — {fc(me,'shadow')}.")
        add("Love", "Love and relationships",
            f"In love it matters to you {fc(ve,'motive')}; you show tenderness {fc(ve,'manner')}. "
            f"Attraction and passion you express {fc(ma,'manner')}. In a partner you instinctively look for {tr(h7)} qualities.")
        add("Energy", "Energy and drive",
            f"You act and pursue what you want {fc(ma,'manner')}. At its best this is {ar(ma,'light')}; "
            f"under pressure you may {fc(ma,'shadow')}.")
        add("Money", "Money and values",
            f"Toward money, things and stability you are {fc(h2,'manner')}. Most of all you value {fc(h2,'motive')}.")
        add("Work", "Work and vocation",
            f"At work and in your career, the approach that suits you is {fc(mc,'manner')}. Your strength in business is {ar(mc,'light')}. "
            f"Focus and discipline come to you {fc(sat,'manner')}.")
    else:
        add("Характер", "Character",
            f"По характеру вы {tr(sun)}. В глубине вами движет {fc(sun,'motive')}. "
            f"Первое впечатление, которое вы производите на людей: {tr(asc)} — это ваша «визитная карточка».")
        add("Эмоции и внутренний мир", "Feelings",
            f"В чувствах и настроении вы {tr(moon)}. Чтобы чувствовать себя спокойно и в безопасности, вам важно {fc(moon,'motive')}. "
            f"В трудные моменты следите, чтобы это не перешло в {fc(moon,'shadow')}.")
        add("Ум и общение", "Mind",
            f"Думаете, учитесь и общаетесь вы {fc(me,'manner')}. Это даёт {ar(me,'light')}, "
            f"но иногда — {fc(me,'shadow')}.")
        add("Любовь и отношения", "Love",
            f"В любви вам важно {fc(ve,'motive')}; нежность вы проявляете {fc(ve,'manner')}. "
            f"Влечение и страсть — {fc(ma,'manner')}. В партнёре вам близки такие черты: {tr(h7)}.")
        add("Энергия и действие", "Energy",
            f"Действуете и добиваетесь своего вы {fc(ma,'manner')}. В лучшем виде это {ar(ma,'light')}; "
            f"под давлением можете {fc(ma,'shadow')}.")
        add("Деньги и ценности", "Money",
            f"К деньгам, вещам и стабильности вы относитесь {fc(h2,'manner')}. Больше всего вы цените {fc(h2,'motive')}.")
        add("Работа и призвание", "Work",
            f"В работе и карьере вам ближе всего подход {fc(mc,'manner')}. Ваша сильная сторона в деле — {ar(mc,'light')}. "
            f"Собранность и дисциплина даются вам {fc(sat,'manner')}.")

    # Плюсы и минусы личности — наглядные списки черт (из ключевых точек карты).
    def collect(key):
        items, seen = [], set()
        for s in (sun, moon, asc, ve, ma):
            for it in _split_traits(ar(s, key)):
                low = it.lower()
                if low not in seen:
                    seen.add(low)
                    items.append(it)
        return items[:7]

    traits = {"pros": collect("light"), "cons": collect("shadow")}

    # Внутренние созвучия и противоречия (аспекты простым языком).
    links = []
    seen = set()
    for a in sorted(aspects or [], key=lambda x: x.get("orbit", 99)):
        p1, p2, nat = a.get("p1"), a.get("p2"), a.get("nature")
        pair = _aspect_pair(p1, p2)
        if not pair or p1 not in PLANET_AREA or p2 not in PLANET_AREA:
            continue
        key = frozenset((p1, p2))
        if key in seen:
            continue
        seen.add(key)
        a1, a2 = g(PLANET_AREA[p1], lang), g(PLANET_AREA[p2], lang)
        if nat == "tense":
            txt = (f"Бывает внутреннее напряжение между сферами «{a1}» и «{a2}»: {g(pair['tension'], lang)}."
                   if lang != "en" else
                   f"There can be inner tension between {a1} and {a2}: {g(pair['tension'], lang)}.")
            tone = "tense"
        else:
            txt = (f"Хорошо сочетаются «{a1}» и «{a2}»: {g(pair['harmony'], lang)}."
                   if lang != "en" else
                   f"{a1.capitalize()} and {a2} go well together: {g(pair['harmony'], lang)}.")
            tone = "good"
        links.append({"tone": tone, "text": txt})
        if len(links) >= 6:
            break

    return {"sections": sections, "traits": traits, "links": links}


def interpret_house(planet_name: str, house_num: Optional[int], lang: str = "ru") -> str:
    if not house_num:
        return ""
    role = PLANET_ROLE.get(planet_name)
    focus = HOUSE_FOCUS.get(house_num)
    if not role or not focus:
        return ""
    planet_ru = C.point_name(planet_name, lang)
    exp = g(HOUSE_EXP.get(house_num, ("", "")), lang)
    overlay = _OVERLAY_PLANET.get(planet_name)
    if lang == "en":
        s = f"{planet_ru} in the {_ord_en(house_num)} house directs “{g(role, lang)}” {g(focus, lang)}. {exp}"
        if overlay:
            s += f" {planet_ru} brings {g(overlay, lang)} here — these qualities are most noticeable in this area of life."
        return s
    s = f"{planet_ru} в {house_num}-м доме направляет «{g(role, lang)}» {g(focus, lang)}. {exp}"
    if overlay:
        s += f" {planet_ru} привносит сюда {g(overlay, lang)} — именно в этой сфере эти качества проявляются заметнее всего."
    return s


# Жизненная тема каждого угла карты — для трактовки аспектов к углам.
# --------------------------------------------------------------------------- #
#  Авторские тексты «аспект к углу карты» (планета × угол × тип аспекта), RU+EN.
#  Ключ — (planet, angle, aspect); angle — "Ascendant" | "Medium_Coeli".
#  Наполняется скриптом-мёржем; редактируется через админку (namespace AUTHORED_ANGLE).
# --------------------------------------------------------------------------- #
# AUTHORED_ANGLE: закрытый слой, см. _load_authored() выше.


def authored_angle(planet_name: str, angle: str, aspect: str, lang: str = "ru") -> str:
    """Авторский текст аспекта планеты к углу карты (ASC/MC), если есть (иначе '')."""
    found = AUTHORED_ANGLE.get((planet_name, angle, aspect))
    if found:
        return g(found, lang) or ""
    return ""


_ANGLE_AREA = {
    "Ascendant": ("вашу личность, облик и манеру входить в мир", "your personality, image and way of entering the world"),
    "Medium_Coeli": ("вашу карьеру, статус и публичную роль", "your career, status and public role"),
    "Descendant": ("ваши партнёрства и значимые отношения", "your partnerships and significant relationships"),
    "Imum_Coeli": ("ваш дом, семью и внутренние корни", "your home, family and inner roots"),
}


def interpret_aspect(p1_name: str, aspect: str, p2_name: str, lang: str = "ru") -> str:
    p1_ru = C.point_name(p1_name, lang)
    p2_ru = C.point_name(p2_name, lang)
    aspect_ru = C.aspect_name(aspect, lang).lower()
    nature = C.ASPECTS.get(aspect, {}).get("nature", "")

    # Авторский текст под конкретную пару И тип аспекта (приоритетнее композиции).
    au = authored_aspect(p1_name, p2_name, aspect, lang)
    if au:
        return f"{p1_ru} {aspect_ru} {p2_ru} — {au}"

    # Авторская трактовка конкретной пары планет (если есть в ASPECT_PAIR).
    pair = _aspect_pair(p1_name, p2_name)
    if pair:
        theme = g(pair["theme"], lang)
        harmony = g(pair["harmony"], lang)
        tension = g(pair["tension"], lang)
        if nature == "harmonious":
            if lang == "en":
                detail = f"This is a flowing aspect — the two energies support each other and give {harmony}. The gift works best when used consciously, otherwise it stays dormant."
            else:
                detail = f"Аспект гармоничный — энергии поддерживают друг друга и дают {harmony}. Дар работает, когда им пользуешься осознанно, иначе остаётся в потенциале."
        elif nature == "tense":
            if lang == "en":
                detail = f"This is a tense aspect — here lies inner conflict and a growth zone: {tension}. Worked through, the same tension becomes a source of strength."
            else:
                detail = f"Аспект напряжённый — здесь внутренний конфликт и зона роста: {tension}. Проработанное, это же напряжение становится источником силы."
        else:  # соединение / нейтральный — сплав, может пойти в обе стороны
            if lang == "en":
                detail = f"The functions fuse into one: in accord this gives {harmony}; when out of balance — {tension}."
            else:
                detail = f"Функции сплавляются воедино: в согласии это даёт {harmony}; при перекосе — {tension}."
        if lang == "en":
            return f"{p1_ru} {aspect_ru} {p2_ru} — {theme}. {detail}"
        return f"{p1_ru} {aspect_ru} {p2_ru} — {theme}. {detail}"

    # Аспект к углу карты (ASC/MC/DSC/IC) — планета окрашивает тему этого угла.
    angle = p1_name if p1_name in _ANGLE_AREA else (p2_name if p2_name in _ANGLE_AREA else None)
    if angle:
        planet = p2_name if angle == p1_name else p1_name
        au = authored_angle(planet, angle, aspect, lang)
        if au:
            return f"{C.point_name(planet, lang)} {aspect_ru} {C.point_name(angle, lang)} — {au}"
        area = g(_ANGLE_AREA[angle], lang)
        bring = _OVERLAY_PLANET.get(planet) or PLANET_ROLE.get(planet)
        if bring:
            bring_t = g(bring, lang)
            angle_ru = C.point_name(angle, lang)
            planet_ru2 = C.point_name(planet, lang)
            if lang == "en":
                clause = {
                    "harmonious": "It is a harmonious aspect — this area is supported easily and naturally",
                    "tense": "It is a tense aspect — this theme calls for conscious work",
                }.get(nature, "A conjunction — this theme is coloured vividly and at full strength")
                return f"{planet_ru2} {aspect_ru} {angle_ru} touches {area}. {clause}, bringing in {bring_t}."
            clause = {
                "harmonious": "Аспект гармоничный — эта сфера поддерживается легко и естественно",
                "tense": "Аспект напряжённый — здесь нужно осознанно работать с этой темой",
            }.get(nature, "Соединение — эта тема окрашивается ярко и в полную силу")
            return f"{planet_ru2} {aspect_ru} {angle_ru} затрагивает {area}. {clause}, привнося {bring_t}."

    # Запасная (общая) трактовка по типу аспекта — для пар с дальними планетами/узлами.
    interp = ASPECT_INTERP.get(aspect)
    if not interp:
        return ""
    role1 = PLANET_ROLE.get(p1_name)
    role2 = PLANET_ROLE.get(p2_name)
    if role1 and role2:
        lead1 = f"{p1_ru} ({g(role1, lang)})"
        lead2 = f"{p2_ru} ({g(role2, lang)})"
    else:
        lead1, lead2 = p1_ru, p2_ru
    if lang == "en":
        return f"{lead1} and {lead2} in a “{aspect_ru}” aspect {g(interp, lang)}."
    return f"{lead1} и {lead2} в аспекте «{aspect_ru}» {g(interp, lang)}."


# --------------------------------------------------------------------------- #
#  Психологический профиль: баланс стихий и крестов
# --------------------------------------------------------------------------- #
ELEMENT_PROFILE = {
    "fire": ("Преобладает Огонь — вами движут энтузиазм, инициатива и вера в себя. Вы действуете порывом, зажигаете других и плохо переносите застой.", "Fire predominates — you are driven by enthusiasm, initiative and self-belief. You act on impulse, inspire others and tolerate stagnation poorly."),
    "earth": ("Преобладает Земля — вы практичны, надёжны и связаны с материальным миром. Цените результат и стабильность, доверяете опыту больше, чем теориям.", "Earth predominates — you are practical, reliable and connected to the material world. You value results and stability, and trust experience more than theories."),
    "air": ("Преобладает Воздух — вы интеллектуальны, общительны и живёте обменом идеями. Мир осмысляете через слово, контакты и анализ.", "Air predominates — you are intellectual, sociable and need an exchange of ideas. You make sense of life through words, contacts and analysis."),
    "water": ("Преобладает Вода — вы чувствительны, интуитивны и эмоционально глубоки. Мир воспринимаете через переживания и тонко улавливаете настроения.", "Water predominates — you are sensitive, intuitive and emotionally deep. You perceive the world through feeling and subtly catch moods."),
}
ELEMENT_LACK = {
    "fire": ("Огня мало — может недоставать спонтанности, азарта и веры в собственные силы.", "Little Fire — you may lack spontaneity, drive and faith in your own strength."),
    "earth": ("Земли мало — возможны трудности с практичностью и доведением идей до материального результата.", "Little Earth — there may be difficulty with practicality and bringing ideas to a material result."),
    "air": ("Воздуха мало — порой сложно отстраниться и осмыслить происходящее рационально.", "Little Air — it can be hard to step back and make rational sense of what is happening."),
    "water": ("Воды мало — эмоциям и интуиции бывает трудно довериться, и чувства остаются в тени.", "Little Water — it can be hard to trust emotions and intuition, and feelings stay in the shadow."),
}
QUALITY_PROFILE = {
    "cardinal": ("Преобладает кардинальный крест — вы инициатор: легко начинаете новое и стремитесь влиять на события.", "The cardinal modality predominates — you are an initiator: you start new things easily and seek to influence events."),
    "fixed": ("Преобладает фиксированный крест — вы устойчивы и настойчивы, доводите начатое до конца, но курс меняете с трудом.", "The fixed modality predominates — you are steady and persistent, finishing what you start, but you change course with difficulty."),
    "mutable": ("Преобладает мутабельный крест — вы гибки и приспособляемы, легко перестраиваетесь, но труднее удерживаете одно направление.", "The mutable modality predominates — you are flexible and adaptable, adjusting easily, but you hold a single direction less easily."),
}


def interpret_balance(element_dist: dict, quality_dist: dict, lang: str = "ru") -> dict:
    elements = {k: element_dist.get(f"{k}_percentage", 0) for k in ("fire", "earth", "air", "water")}
    dom_el = max(elements, key=elements.get)
    texts = [g(ELEMENT_PROFILE[dom_el], lang)]
    for el, pct in elements.items():
        if pct <= 10 and el != dom_el:
            texts.append(g(ELEMENT_LACK[el], lang))

    qualities = {k: quality_dist.get(f"{k}_percentage", 0) for k in ("cardinal", "fixed", "mutable")}
    dom_q = max(qualities, key=qualities.get)
    texts.append(g(QUALITY_PROFILE[dom_q], lang))

    el_name = C._l(C.ELEMENT_NAMES[dom_el], lang)
    q_name = {"cardinal": ("Кардинальный", "Cardinal"), "fixed": ("Фиксированный", "Fixed"), "mutable": ("Мутабельный", "Mutable")}[dom_q]
    return {
        "dominant_element": el_name,
        "dominant_quality": g(q_name, lang),
        "text": " ".join(texts),
    }


# --------------------------------------------------------------------------- #
#  Синтез светил и Асцендента, управитель карты
# --------------------------------------------------------------------------- #
SIGN_CORE = {
    "Ari": ("смелость, энергию и стремление действовать первым", "courage, energy and the drive to act first"),
    "Tau": ("устойчивость, чувственность и потребность в надёжности", "steadiness, sensuality and the need for reliability"),
    "Gem": ("любознательность, гибкость ума и лёгкость в общении", "curiosity, mental agility and ease in communication"),
    "Can": ("заботливость, эмоциональную память и привязанность к дому", "caring, emotional memory and attachment to home"),
    "Leo": ("творческое самовыражение, щедрость и потребность в признании", "creative self-expression, generosity and the need for recognition"),
    "Vir": ("практичность, внимание к деталям и стремление быть полезным", "practicality, attention to detail and the wish to be useful"),
    "Lib": ("тягу к гармонии, дипломатичность и ориентацию на партнёрство", "a longing for harmony, diplomacy and an orientation toward partnership"),
    "Sco": ("глубину, страстность и способность к преображению", "depth, passion and the capacity for transformation"),
    "Sag": ("оптимизм, любовь к свободе и поиск смысла", "optimism, love of freedom and the search for meaning"),
    "Cap": ("целеустремлённость, ответственность и опору на результат", "determination, responsibility and a focus on results"),
    "Aqu": ("оригинальность, независимость и устремлённость в будущее", "originality, independence and a reach toward the future"),
    "Pis": ("сострадание, воображение и тонкую душевную организацию", "compassion, imagination and a fine inner sensitivity"),
}

SIGN_RULER = {
    "Ari": "Mars", "Tau": "Venus", "Gem": "Mercury", "Can": "Moon", "Leo": "Sun",
    "Vir": "Mercury", "Lib": "Venus", "Sco": "Mars", "Sag": "Jupiter",
    "Cap": "Saturn", "Aqu": "Saturn", "Pis": "Jupiter",
}


# --------------------------------------------------------------------------- #
#  Психологический портрет личности
# --------------------------------------------------------------------------- #
_TEMP_QUALITY = {
    "cardinal": ("Перевес кардинального креста добавляет инициативности — вы скорее запускаете процессы, чем поддерживаете их.",
                 "A cardinal emphasis adds initiative — you tend to launch processes rather than sustain them."),
    "fixed": ("Перевес фиксированного креста добавляет устойчивости и упорства — вы держите курс, но меняетесь тяжелее.",
              "A fixed emphasis adds stability and persistence — you hold your course but change with difficulty."),
    "mutable": ("Перевес мутабельного креста добавляет гибкости — вы легко подстраиваетесь, но труднее держите одну линию.",
                "A mutable emphasis adds flexibility — you adjust easily but find it harder to hold one line."),
}
TEMPERAMENT = {
    "fire": {"name": ("Холерик", "Choleric"),
             "text": ("энергичный, быстрый и страстный тип — вы загораетесь идеями, действуете напористо и ведёте за собой, но склонны к нетерпению и вспыльчивости",
                      "an energetic, fast and passionate type — you ignite with ideas, act assertively and lead, but tend toward impatience and a quick temper")},
    "air": {"name": ("Сангвиник", "Sanguine"),
            "text": ("общительный, лёгкий и любознательный тип — вы живёте контактами, идеями и переменами, легко адаптируетесь, но можете распыляться и скучать без новизны",
                     "a sociable, light and curious type — you live by contacts, ideas and change, adapt easily, but may scatter and grow bored without novelty")},
    "water": {"name": ("Флегматик", "Phlegmatic"),
              "text": ("чувствительный, спокойный и глубокий тип — вы воспринимаете мир через эмоции и интуицию, привязчивы и заботливы, но ранимы и склонны уходить в себя",
                       "a sensitive, calm and deep type — you perceive the world through emotion and intuition, are attached and caring, but vulnerable and prone to withdrawing")},
    "earth": {"name": ("Меланхолик", "Melancholic"),
              "text": ("основательный, практичный и надёжный тип — вы цените стабильность, доводите дела до результата и опираетесь на факты, но склонны к осторожности и самокритике",
                       "a grounded, practical and reliable type — you value stability, see things through and rely on facts, but tend toward caution and self-criticism")},
}


def temperament(element_dist: dict, quality_dist: dict, lang: str = "ru") -> dict:
    elements = {k: element_dist.get(f"{k}_percentage", 0) for k in ("fire", "earth", "air", "water")}
    dom = max(elements, key=elements.get)
    t = TEMPERAMENT[dom]
    qualities = {k: quality_dist.get(f"{k}_percentage", 0) for k in ("cardinal", "fixed", "mutable")}
    dom_q = max(qualities, key=qualities.get)
    q_note = g(_TEMP_QUALITY[dom_q], lang)
    name = g(t["name"], lang)
    if lang == "en":
        text = f"Your temperament is predominantly {name.lower()} — {g(t['text'], lang)}. {q_note}"
    else:
        text = f"Темперамент преимущественно {name.lower()}: {g(t['text'], lang)}. {q_note}"
    return {"name": name, "element": dom, "text": text}


def missing_element(element_dist: dict, lang: str = "ru") -> list:
    out = []
    for el in ("fire", "earth", "air", "water"):
        if element_dist.get(f"{el}_percentage", 0) == 0:
            out.append({"element": C._l(C.ELEMENT_NAMES[el], lang), "text": g(ELEMENT_LACK[el], lang)})
    return out


_AXIS_DEFS = [
    ("Sun", ("Мотивация и эго", "Motivation and ego"), ("самоутверждается и проявляет волю", "asserts itself and shows will")),
    ("Moon", ("Эмоции и потребности", "Emotions and needs"), ("чувствует и ищет безопасность", "feels and seeks safety")),
    ("Mercury", ("Мышление и речь", "Thinking and speech"), ("думает, учится и говорит", "thinks, learns and speaks")),
    ("Mars", ("Воля и гнев", "Will and anger"), ("действует, желает и злится", "acts, desires and gets angry")),
    ("Saturn", ("Страхи и защиты", "Fears and defenses"), ("сдерживает себя и защищается", "restrains itself and defends")),
]


def psych_axes(planet_signs: dict, lang: str = "ru") -> list:
    out = []
    for pname, label, verb in _AXIS_DEFS:
        sign = planet_signs.get(pname)
        facets = SIGN_FACETS.get(sign)
        if not sign or not facets:
            continue
        manner = g(facets["manner"], lang)
        sign_ru = C.sign_name(sign, lang)
        psym = C.point_name(pname, lang)
        text = f"{psym} {C.sign_in(sign, lang)}: {g(verb, lang)} {manner}"
        if pname == "Saturn":
            text += "; здесь зона страха, контроля и взросления." if lang != "en" else "; this is the zone of fear, control and maturing."
        else:
            text += "."
        out.append({"label": g(label, lang), "text": text})
    return out


DOMINANT_PLANET = {
    "Sun": ("Личность держится на самовыражении, воле и жажде быть значимым — всё проходит проверку через «я хочу» и «я есть».",
            "The personality revolves around self-expression, will and the need to matter — everything is filtered through “I want” and “I am”."),
    "Moon": ("Всем правят чувства, потребность в безопасности и забота: настроение и привязанности становятся главным двигателем.",
             "Life is governed by feelings, the need for safety and care — mood and attachments become the main driver."),
    "Mercury": ("Ведёт ум, любопытство и общение — человек живёт информацией, словом, анализом и связями.",
                "The lead is the mind, curiosity and communication: the person lives by information, words, analysis and contacts."),
    "Venus": ("В центре — любовь, отношения, красота и ценности: важнее всего гармония, удовольствие и притяжение.",
              "At the centre are love, relationships, beauty and values: harmony, pleasure and attraction matter most."),
    "Mars": ("Главное — действие, воля и желание: человек живёт инициативой, борьбой и достижением цели.",
             "The main thing is action, will and desire: the person lives through initiative, struggle and reaching goals."),
    "Jupiter": ("Ведут рост, смысл и расширение — человеком движут вера, оптимизм и жажда большего.",
                "The lead is growth, meaning and expansion: the person is driven by faith, optimism and the search for more."),
    "Saturn": ("В центре — ответственность, структура и дисциплина: жизнь строится на долге, контроле и мастерстве.",
               "At the centre are responsibility, structure and discipline: life is built through duty, control and mastery."),
    "Uranus": ("Главное — свобода, оригинальность и перемены: человек живёт независимостью и новаторством.",
               "The main thing is freedom, originality and change: the person lives through independence and innovation."),
    "Neptune": ("Ведут воображение, чувствительность и идеал — человек живёт мечтой, состраданием и тонким восприятием.",
                "The lead is imagination, sensitivity and the ideal: the person lives by dream, compassion and subtle perception."),
    "Pluto": ("В центре — глубина, власть и трансформация: человек живёт интенсивностью, контролем и перерождением.",
              "At the centre are depth, power and transformation: the person lives through intensity, control and rebirth."),
}


def dominant_planet_text(name: str, lang: str = "ru") -> str:
    d = DOMINANT_PLANET.get(name)
    return g(d, lang) if d else ""


_SELF_ESTEEM = {
    "critic": ("Внутренний критик строг, и самооценка вечно сверяется с вопросом «достаточно ли я сделал». Опора приходит с возрастом и реальными достижениями — важно не путать строгость к себе с правдой о себе.",
               "The inner critic is strict: self-worth is tested by “have I done enough”. Support comes with age and real achievement — it's important not to confuse harshness toward yourself with the truth about yourself."),
    "steady": ("Здоровая внутренняя опора: уверенность растёт постепенно и надёжно — через зрелость, ответственность и накопленный опыт.",
               "A healthy inner foundation: confidence is built gradually and steadily — through maturity, responsibility and accumulated experience."),
    "tempered": ("Самооценка подвижна и закаляется в напряжении: внутреннее «я» крепнет в преодолении, хотя порой колеблется под давлением обстоятельств.",
                 "Self-worth is mobile and tempered through tension: the inner self grows by overcoming, though it sometimes wavers under pressure."),
    "free": ("Самоощущение в целом устойчивое: «я» проявляется свободно, без сильного внутреннего давления и вечной самопроверки.",
             "An overall stable sense of self: the “I” expresses freely, without heavy inner pressure or constant self-checking."),
}


def self_esteem(sun_saturn: Optional[str], sun_hard: int, sun_soft: int, lang: str = "ru") -> str:
    if sun_saturn == "hard":
        return g(_SELF_ESTEEM["critic"], lang)
    if sun_saturn == "soft":
        return g(_SELF_ESTEEM["steady"], lang)
    if sun_hard > sun_soft:
        return g(_SELF_ESTEEM["tempered"], lang)
    return g(_SELF_ESTEEM["free"], lang)


def synthesize_core(sun_sign: str, moon_sign: str, asc_sign: str, lang: str = "ru") -> str:
    sun = SIGN_CORE.get(sun_sign)
    moon = SIGN_CORE.get(moon_sign)
    asc = SIGN_CORE.get(asc_sign)
    parts = []
    if lang == "en":
        if sun:
            parts.append(f"The core of the personality (Sun {C.sign_in(sun_sign, lang)}) unfolds through {g(sun, lang)}.")
        if moon:
            parts.append(f"The emotional nature and inner needs (Moon {C.sign_in(moon_sign, lang)}) are {g(moon, lang)}.")
        if asc:
            parts.append(f"The outer self and first impression (Ascendant {C.sign_in(asc_sign, lang)}) show up through {g(asc, lang)}.")
    else:
        if sun:
            parts.append(f"Ядро личности (Солнце {C.sign_in(sun_sign, lang)}) раскрывается через {g(sun, lang)}.")
        if moon:
            parts.append(f"Эмоциональная природа и внутренние потребности (Луна {C.sign_in(moon_sign, lang)}) — это {g(moon, lang)}.")
        if asc:
            parts.append(f"Внешнее «я» и первое впечатление (Асцендент {C.sign_in(asc_sign, lang)}) проявляются через {g(asc, lang)}.")
    return " ".join(parts)


def chart_ruler(asc_sign: str) -> Optional[str]:
    return SIGN_RULER.get(asc_sign)


# Современные со-управители (высшие планеты) для знаков, которыми традиционно правят Марс/Сатурн/Юпитер.
MODERN_CORULER = {"Sco": "Pluto", "Aqu": "Uranus", "Pis": "Neptune"}


def modern_coruler(asc_sign: str) -> Optional[str]:
    return MODERN_CORULER.get(asc_sign)


# --------------------------------------------------------------------------- #
#  Светила: назначение (для подсказок по большой тройке)
# --------------------------------------------------------------------------- #
LUMINARY_PURPOSE = {
    "Sun": {
        "ru": ("Солнце", "наше предназначение, воля и жизненная цель — то, ради чего мы живём и кем призваны стать; источник жизненной силы и сознательного «Я»"),
        "en": ("Sun", "our purpose, will and life goal — what we live for and who we are called to become; the source of vitality and the conscious self"),
    },
    "Moon": {
        "ru": ("Луна", "фильтр восприятия и внутренняя психология — как мы чувствуем, в чём нуждаемся и что даёт ощущение безопасности; реакции, привычки и память души"),
        "en": ("Moon", "the filter of perception and inner psychology — how we feel, what we need and what gives a sense of safety; reactions, habits and the memory of the soul"),
    },
    "Ascendant": {
        "ru": ("Асцендент", "инструмент, которым мы пользуемся — стиль действия, «маска» и способ входить в мир, наша мгновенная реакция и первое впечатление"),
        "en": ("Ascendant", "the instrument we use — the style of action, the “mask” and the way we enter the world, our immediate reaction and first impression"),
    },
}

# --------------------------------------------------------------------------- #
#  Архетипы знаков (астропсихология): суть, светлая и теневая сторона
# --------------------------------------------------------------------------- #
SIGN_ARCHETYPE = {
    "Ari": {
        "archetype": ("Воин, Первопроходец", "The Warrior, the Pioneer"),
        "essence": ("чистая инициирующая энергия, импульс к действию и началу", "pure initiating energy, the impulse to act and to begin"),
        "light": ("смелость, решительность, честность, лидерство и умение начинать", "courage, decisiveness, honesty, leadership and the ability to start"),
        "shadow": ("вспыльчивость, эгоцентризм, нетерпеливость и неумение доводить до конца", "hot temper, egocentrism, impatience and difficulty finishing things"),
        "detail": (
            "Овном движет чистый глагол «действовать». Там, где другие ещё взвешивают, он уже сделал первый шаг — и в этом одновременно его дар и его беда. Внутри живёт ребёнок-первопроходец: ему нужно завоёвывать, пробовать, быть первым, встречать сопротивление и преодолевать его. Скука и ожидание для Овна мучительнее любого поражения.\n\nВ отношениях он честен до прямоты и завоёвывает открыто, но быстро остывает, когда исчезает азарт погони. В работе незаменим на старте — запустить, пробить, зажечь команду, — а рутину и доведение до финиша лучше отдать другим. Главный внутренний конфликт: энергия рвётся наружу быстрее, чем приходит терпение её направить. Взрослея, Овен учится не гасить огонь, а держать его — превращать вспышку в ровное горение, злость в смелость, «я первый» в «я поведу за собой».",
            "Aries is driven by a single verb: to act. Where others are still weighing options, an Aries has already taken the first step — and that is both the gift and the trouble. Inside lives a pioneer-child who needs to conquer, to try, to be first, to meet resistance and overcome it; waiting and boredom are more painful to Aries than any defeat.\n\nIn love they court openly and honestly but cool quickly once the thrill of the chase is gone. At work they are invaluable at the start — to launch, to break through, to fire up a team — while routine and finishing are better left to others. The core tension: energy bursts out faster than the patience to aim it. Growing up, Aries learns not to smother the fire but to hold it — turning a flash into a steady flame, anger into courage, 'me first' into 'follow me'.",
        ),
    },
    "Tau": {
        "archetype": ("Хранитель, Садовник", "The Keeper, the Gardener"),
        "essence": ("укоренённость в материи, чувственность и созидание устойчивости", "rootedness in matter, sensuality and the building of stability"),
        "light": ("надёжность, терпение, верность, практичность и вкус к жизни", "reliability, patience, loyalty, practicality and a taste for life"),
        "shadow": ("упрямство, инертность, собственничество и страх перемен", "stubbornness, inertia, possessiveness and fear of change"),
        "detail": (
            "Телец приходит в мир, чтобы обжить его телом и руками. Ему важно не абстрактное, а осязаемое: вкус, тепло, надёжность, то, что можно потрогать и чем можно спокойно владеть. Он никуда не спешит — и в этой неспешности его сила: то, что Телец строит, стоит долго, потому что построено на совесть.\n\nВ любви он верен, чувственен и основателен, дарит партнёру ощущение защищённости, но ревнует и тяжело отпускает — привязанность легко превращается в собственничество. В деле он выносливый марафонец: доведёт начатое до конца, когда остальные бросили, зато сдвинуть его с наезженной колеи почти невозможно. Его внутренний узел — страх перемен: устойчивость, которая держит, при перегибе становится тюрьмой из привычек. Зрелость Тельца — научиться разжимать ладонь: беречь ценное, но не путать обладание с любовью, а покой — с застоем.",
            "Taurus comes into the world to inhabit it through the body and the hands. What matters is not the abstract but the tangible: taste, warmth, reliability, what can be touched and calmly owned. There is no hurry — and in that lies the strength: what Taurus builds lasts, because it is built to last.\n\nIn love they are faithful, sensual and grounding, giving a partner a sense of safety, but they are jealous and slow to let go — attachment easily hardens into possessiveness. At work they are the enduring marathoner who finishes what others abandoned, yet moving them off a worn track is nearly impossible. Their inner knot is the fear of change: the stability that steadies them can, taken too far, become a prison of habits. Maturity for Taurus is learning to unclench the hand — to keep what is precious without mistaking ownership for love, or calm for stagnation.",
        ),
    },
    "Gem": {
        "archetype": ("Посланник, Вечный ученик", "The Messenger, the Eternal Student"),
        "essence": ("подвижный ум, любознательность и связь через слово", "a mobile mind, curiosity and connection through words"),
        "light": ("гибкость, остроумие, общительность и быстрая обучаемость", "flexibility, wit, sociability and quick learning"),
        "shadow": ("поверхностность, разбросанность, непостоянство и суетливость", "superficiality, scatteredness, inconstancy and restlessness"),
        "detail": (
            "Близнецы живут скоростью мысли. Мир для них — бесконечная библиотека, где хочется полистать всё сразу: новое знакомство, новую тему, новый поворот разговора. Слово — их стихия и инструмент: они связывают людей, идеи и факты, переводят сложное на понятный язык и заражают любопытством.\n\nВ отношениях им нужен собеседник прежде любовника — молчаливая скука для Близнецов страшнее ссоры. Они лёгкие и остроумные, но непостоянные: интерес вспыхивает ярко и так же быстро перескакивает дальше. В работе блестящи там, где нужны переговоры, тексты, многозадачность и быстрое переключение, а глубина и усидчивость даются с трудом. Их конфликт — распыление: тысяча начатых дел и ни одного законченного, ум, который бежит впереди чувств. Взрослея, Близнецы учатся не хвататься за всё подряд, а выбирать одно и доходить до сути — превращать эрудицию в настоящую мудрость.",
            "Gemini lives at the speed of thought. The world is an endless library they long to leaf through all at once: a new acquaintance, a new topic, a new turn in the conversation. Words are their element and their tool — they connect people, ideas and facts, translate the complex into plain language and spread curiosity.\n\nIn relationships they need a conversationalist before a lover; silent boredom frightens a Gemini more than a quarrel. They are light and witty but inconstant — interest flares brightly and just as quickly leaps elsewhere. At work they shine wherever negotiation, writing, multitasking and quick switching are needed, while depth and persistence come hard. Their conflict is scattering: a thousand things begun and none finished, a mind that outruns the heart. Growing up, Gemini learns not to grab at everything but to choose one thing and reach its essence — turning cleverness into real wisdom.",
        ),
    },
    "Can": {
        "archetype": ("Хранитель очага, Мать", "The Keeper of the Hearth, the Mother"),
        "essence": ("эмоциональная глубина, забота и потребность в принадлежности", "emotional depth, care and the need to belong"),
        "light": ("чуткость, преданность, интуиция и умение создавать тепло", "sensitivity, devotion, intuition and the ability to create warmth"),
        "shadow": ("обидчивость, тревожность, цепляние за прошлое и манипуляции чувствами", "touchiness, anxiety, clinging to the past and manipulation through feelings"),
        "detail": (
            "Рак чувствует мир кожей. Там, где другие рассуждают, он ощущает — настроение в комнате, невысказанную боль близкого, то, что «что-то не так». Его дом, его люди, его память — вот подлинная территория Рака; он создаёт тепло и принадлежность, без которых сам вянет.\n\nВ любви он преданный и заботливый, окутывает вниманием, но и уязвим: обиду носит долго, а страх быть покинутым толкает то к цеплянию, то к манипуляции чувством вины. В работе силён там, где нужно заботиться, удерживать атмосферу, чувствовать людей, — но тонкую кожу приходится защищать от грубости среды. Его внутренний узел — прошлое: память, что питает, легко превращается в якорь, не пускающий вперёд. Зрелость Рака — научиться заботиться, не растворяясь, и отпускать прожитое, оставляя себе тепло, а не рану.",
            "Cancer feels the world through the skin. Where others reason, a Cancer senses — the mood in a room, a loved one's unspoken pain, the feeling that 'something is off'. Home, their people, their memory — this is Cancer's true territory; they create the warmth and belonging without which they themselves wilt.\n\nIn love they are devoted and caring, wrapping others in attention, but also vulnerable: they hold a hurt for a long time, and the fear of being abandoned pushes them now to cling, now to guilt-driven manipulation. At work they are strong wherever caring, holding the atmosphere and sensing people matter, though that thin skin needs shielding from a rough environment. Their inner knot is the past: the memory that nourishes can turn into an anchor that will not let them move on. Maturity for Cancer is learning to care without dissolving, and to release what has already been lived, keeping the warmth rather than the wound.",
        ),
    },
    "Leo": {
        "archetype": ("Король, Творец", "The King, the Creator"),
        "essence": ("творческая воля к самовыражению и сиянию", "the creative will to self-expression and to shine"),
        "light": ("великодушие, харизма, верность, щедрость и достоинство", "magnanimity, charisma, loyalty, generosity and dignity"),
        "shadow": ("гордыня, тщеславие, потребность в постоянном восхищении и драматизация", "pride, vanity, the need for constant admiration and drama"),
        "detail": (
            "Лев рождён светить. В нём живёт врождённое достоинство и потребность выразить себя — через творчество, любовь, игру, щедрый жест. Зрители нужны ему не из тщеславия, а потому что радость Льва настоящая только тогда, когда её с кем-то делят. Он согревает, вдохновляет и ведёт за собой теплом, а не приказом.\n\nВ любви он щедр, верен и театрален, любит красиво и крупно, но требует восхищения и тяжело переносит равнодушие. В работе — прирождённый лидер и вдохновитель там, где нужно зажечь и повести, но его подводит гордость и неумение признавать ошибки. Главный конфликт — самооценка, зависящая от аплодисментов: за царственной уверенностью часто прячется ребёнок, которому нужно, чтобы его любили. Зрелость Льва — научиться светить не ради оваций, а потому что это его природа, и находить величие в великодушии, а не в превосходстве.",
            "Leo is born to shine. There is an innate dignity and a need for self-expression — through creativity, love, play, a generous gesture. Leo needs an audience not out of vanity but because their joy is only real when shared; they warm and inspire and lead by warmth rather than command.\n\nIn love they are generous, loyal and theatrical, loving large and beautifully, but they crave admiration and take indifference hard. At work they are a born leader and motivator, wherever it takes fire to get people moving, though pride and difficulty admitting mistakes trip them up. The core conflict is self-worth that hangs on applause: behind the regal confidence often hides a child who needs to be loved. Maturity for Leo is learning to shine not for the ovation but because it is their nature, and to find greatness in generosity rather than superiority.",
        ),
    },
    "Vir": {
        "archetype": ("Мастер, Целитель", "The Craftsman, the Healer"),
        "essence": ("стремление к совершенству, порядку и служению", "the striving for perfection, order and service"),
        "light": ("трудолюбие, внимательность, скромность и реальная польза", "diligence, attentiveness, modesty and real usefulness"),
        "shadow": ("придирчивость, тревога, самокритика и зацикленность на мелочах", "fault-finding, anxiety, self-criticism and fixation on trifles"),
        "detail": (
            "Дева видит то, что можно улучшить. Там, где другие довольствуются «нормально», она замечает шероховатость и хочет довести до ума — не из придирчивости, а из любви к делу, сделанному хорошо. Её стихия — польза: конкретная, ежедневная, та, что делает жизнь чище, здоровее и работоспособнее.\n\nВ отношениях Дева проявляет любовь заботой и делом, а не громкими словами: починит, подскажет, будет рядом в мелочах, — но её тепло легко спрятать за критикой. В работе незаменима там, где важны точность, анализ, порядок и внимание к деталям. Её внутренний узел — перфекционизм, обёрнутый в тревогу: планка «идеально» бьёт по ней самой сильнее, чем по другим, а мелочи заслоняют целое. Зрелость Девы — понять, что служить можно и себе, что «достаточно хорошо» тоже имеет право быть, и что настоящая забота начинается с милосердия к собственному несовершенству.",
            "Virgo sees what can be improved. Where others settle for 'fine', a Virgo notices the rough edge and wants to set it right — not from fault-finding but from love of a thing done well. Their element is usefulness: concrete, daily, the kind that makes life cleaner, healthier and more workable.\n\nIn relationships Virgo shows love through care and deeds rather than grand words: they will fix it, advise, be there in the small things — though that warmth can hide behind criticism. At work they are indispensable wherever precision, analysis, order and attention to detail count. Their inner knot is perfectionism wrapped in anxiety: the bar of 'flawless' strikes them harder than anyone, and details eclipse the whole. Maturity for Virgo is realizing one may serve oneself too, that 'good enough' has a right to exist, and that real care begins with mercy toward one's own imperfection.",
        ),
    },
    "Lib": {
        "archetype": ("Дипломат, Художник", "The Diplomat, the Artist"),
        "essence": ("поиск гармонии, баланса и красоты в отношениях", "the search for harmony, balance and beauty in relationships"),
        "light": ("тактичность, справедливость, обаяние и чувство меры", "tact, fairness, charm and a sense of proportion"),
        "shadow": ("нерешительность, зависимость от других, избегание конфликтов и лицемерие", "indecision, dependence on others, conflict avoidance and hypocrisy"),
        "detail": (
            "Весам невыносима дисгармония. Они чувствуют перекос — в отношениях, в пространстве, в справедливости — и инстинктивно тянутся выровнять, примирить, сделать красиво. Другой человек для Весов не фон, а зеркало: по-настоящему они раскрываются в паре, в диалоге, во взаимности.\n\nВ любви они обаятельны, тактичны и внимательны к партнёру, умеют слышать, но растворяются в чужих желаниях и боятся сказать «нет», чтобы не разрушить мир. В работе сильны там, где нужны переговоры, эстетика, дипломатия, умение свести разные интересы. Их конфликт — нерешительность: бесконечное взвешивание «за» и «против», за которым прячется страх столкновения и потери одобрения. Зрелость Весов — обрести собственную ось: понять, что честная граница не разрушает отношения, а делает их настоящими, и что гармония с собой важнее гладкости с каждым.",
            "Libra cannot bear disharmony. They feel the imbalance — in a relationship, in a room, in fairness — and instinctively move to even it out, to reconcile, to make things beautiful. Another person is not background to Libra but a mirror: they truly come alive in a pair, in dialogue, in mutuality.\n\nIn love they are charming, tactful and attentive to a partner, good at listening, but they dissolve into others' wishes and fear saying 'no' lest they break the peace. At work they are strong wherever negotiation, aesthetics, diplomacy and reconciling different interests are needed. Their conflict is indecision: an endless weighing of pros and cons, behind which hide a fear of conflict and of losing approval. Maturity for Libra is finding their own axis — seeing that an honest boundary does not destroy a relationship but makes it real, and that harmony with oneself matters more than smoothness with everyone.",
        ),
    },
    "Sco": {
        "archetype": ("Алхимик, Феникс", "The Alchemist, the Phoenix"),
        "essence": ("погружение в глубины, страсть и трансформация через кризис", "immersion into the depths, passion and transformation through crisis"),
        "light": ("проницательность, сила воли, верность и способность к перерождению", "insight, willpower, loyalty and the capacity for rebirth"),
        "shadow": ("ревность, мстительность, контроль и разрушительные крайности", "jealousy, vengefulness, control and destructive extremes"),
        "detail": (
            "Скорпион не выносит поверхностности. Его тянет туда, где горячо и глубоко: к правде за фасадом, к страсти, к тайнам, к тому, через что другие боятся пройти. Он живёт циклами смерти и возрождения — сгорает и восстаёт, и каждый кризис для него не катастрофа, а перерождение.\n\nВ любви он предан до самозабвения и требует такой же полной отдачи: полутонов он не признаёт, близость для него — слияние без остатка, оттого и ревность, и жажда контроля. В работе силён там, где нужны проницательность, выдержка, работа с кризисом, скрытым и запретным. Его внутренний узел — власть: сила видеть насквозь легко оборачивается манипуляцией, а боль — жаждой мести. Зрелость Скорпиона — направить свою мощь на преображение, а не на разрушение: отпускать вместо того, чтобы держать мёртвой хваткой, и превращать раны в источник глубины и сострадания.",
            "Scorpio cannot stand the superficial. They are drawn to where it is hot and deep: to the truth behind the facade, to passion, to secrets, to what others are afraid to pass through. They live in cycles of death and rebirth — burning down and rising again, and each crisis is to them not a catastrophe but a transformation.\n\nIn love they are devoted to the point of self-abandon and demand the same total giving: they do not accept half-tones, closeness for them is a merging without remainder, hence the jealousy and the hunger for control. At work they are strong wherever insight, endurance and work with crisis, the hidden and the forbidden are needed. Their inner knot is power: the strength to see through people easily turns into manipulation, and pain into a thirst for revenge. Maturity for Scorpio is aiming that force at transformation rather than destruction — letting go instead of holding in a death-grip, and turning wounds into a source of depth and compassion.",
        ),
    },
    "Sag": {
        "archetype": ("Странник, Философ", "The Wanderer, the Philosopher"),
        "essence": ("стремление к смыслу, свободе и расширению горизонтов", "the striving for meaning, freedom and the widening of horizons"),
        "light": ("оптимизм, щедрость, искренность и вера в будущее", "optimism, generosity, sincerity and faith in the future"),
        "shadow": ("самоуверенность, нетерпимость, безответственность и склонность поучать", "overconfidence, intolerance, irresponsibility and a tendency to lecture"),
        "detail": (
            "Стрельцу нужен горизонт. Его гонит вперёд жажда смысла и простора: новые страны, идеи, вера, всё, что раздвигает рамки. Он оптимист по устройству — верит, что впереди лучше, и этой верой заражает других, зовя в дорогу и вширь.\n\nВ любви он щедр, честен и лёгок, дарит партнёру ощущение полёта, но свободу ценит выше уюта и пугается клетки обязательств. В работе хорош там, где нужны размах, обучение, дальние цели, идеология, — рутина и мелкий контроль его гасят. Его конфликт — между «всё обещать» и «за всё отвечать»: широкие жесты и уверенность легко переходят в самонадеянность и склонность поучать вместо того, чтобы слушать. Зрелость Стрельца — заземлить свою правду: понять, что настоящая свобода не в бегстве от обязательств, а в верности выбранному пути, и что мудрость тише, чем проповедь.",
            "Sagittarius needs a horizon. They are driven forward by a hunger for meaning and open space: new countries, ideas, faith, everything that widens the frame. An optimist by design, they believe the best lies ahead and infect others with that belief, calling them onto the road and outward.\n\nIn love they are generous, honest and light, giving a partner a sense of flight, but they value freedom above comfort and take fright at the cage of obligation. At work they are good wherever scope, teaching, distant goals and vision matter — routine and petty control smother them. Their conflict is between promising everything and being answerable for it: broad gestures and confidence slide easily into overconfidence and a tendency to preach instead of listen. Maturity for Sagittarius is grounding their truth — seeing that real freedom is not in fleeing obligation but in loyalty to a chosen path, and that wisdom is quieter than a sermon.",
        ),
    },
    "Cap": {
        "archetype": ("Строитель, Мудрый старец", "The Builder, the Wise Elder"),
        "essence": ("дисциплина, ответственность и восхождение к вершине", "discipline, responsibility and the climb to the summit"),
        "light": ("целеустремлённость, надёжность, выдержка и стратегическое мышление", "determination, reliability, endurance and strategic thinking"),
        "shadow": ("жёсткость, пессимизм, холодность и подавление чувств ради цели", "rigidity, pessimism, coldness and the suppression of feelings for the goal"),
        "detail": (
            "Козерог мыслит вершинами и годами. Ему свойственна редкая выдержка: он готов идти долго и в гору, отказывая себе в лёгком сейчас ради весомого потом. Ответственность для него не бремя, а способ быть — он опора, на которую можно положиться, тот, кто доведёт.\n\nВ любви он сдержан и надёжен, говорит делом, а не словами, строит всерьёз и надолго, — но за бронёй самообладания прячет чувства, которые боится показать слабостью. В работе — стратег и труженик, силён там, где нужны дисциплина, структура, долгая игра и власть. Его внутренний узел — суровость: требовательность к себе оборачивается холодом и вечным «ещё не заслужил», а цель заслоняет живую жизнь. Зрелость Козерога — разрешить себе тепло и радость по дороге к вершине: понять, что он ценен не только достижениями и что позволить себе чувствовать — не слабость, а вершина повыше любой карьерной.",
            "Capricorn thinks in summits and years. Theirs is a rare endurance: they will walk a long uphill road, denying themselves the easy now for a weighty later. Responsibility is not a burden but a way of being — they are the support you can lean on, the one who will see it through.\n\nIn love they are reserved and reliable, speaking through deeds rather than words, building seriously and for the long term — but behind the armor of self-control they hide feelings they fear to show as weakness. At work they are the strategist and the toiler, strong wherever discipline, structure, the long game and authority are needed. Their inner knot is severity: the demand they place on themselves turns into coldness and an eternal 'not earned yet', while the goal eclipses living life. Maturity for Capricorn is allowing warmth and joy along the way to the summit — seeing that they are worth more than their achievements, and that letting oneself feel is not weakness but a peak higher than any career.",
        ),
    },
    "Aqu": {
        "archetype": ("Реформатор, Гений", "The Reformer, the Genius"),
        "essence": ("свобода, оригинальность и устремлённость в будущее", "freedom, originality and a reach toward the future"),
        "light": ("независимость, новаторство, гуманизм и широта взглядов", "independence, innovation, humanism and breadth of outlook"),
        "shadow": ("отстранённость, упрямый бунт, эмоциональная холодность и эпатаж", "detachment, stubborn rebellion, emotional coldness and provocation"),
        "detail": (
            "Водолей смотрит из будущего. Ему тесно в общепринятом: он видит, как могло бы быть иначе — лучше, свободнее — и не понимает, почему все держатся за старое. Он мыслит человечеством и идеями, дорожит независимостью и правом быть не как все.\n\nВ отношениях он верный друг прежде страстного любовника: ценит свободу, равенство и общность взглядов, но эмоциональная близость даётся ему труднее интеллектуальной — чувства он порой держит на расстоянии. В работе блестящ там, где нужны новизна, технологии, нестандартный взгляд, командная идея, — а иерархия и «так принято» его отталкивают. Его конфликт — между «люблю человечество» и «сложно с конкретным человеком»: за отстранённостью и бунтом ради бунта прячется страх раствориться и потерять себя. Зрелость Водолея — соединить свободу с теплом: понять, что близость не отменяет самобытности и что менять мир начинают с готовности быть по-настоящему рядом с одним живым человеком.",
            "Aquarius looks in from the future. The conventional feels cramped to them: they see how things could be otherwise — better, freer — and cannot understand why everyone clings to the old. They think in terms of humanity and ideas, and prize independence and the right to be unlike the rest.\n\nIn relationships they are a loyal friend before a passionate lover: they value freedom, equality and shared views, but emotional closeness comes harder to them than intellectual, and feelings are sometimes kept at a distance. At work they are brilliant wherever novelty, technology, an unconventional angle and a shared idea are needed — while hierarchy and 'the way it's done' repel them. Their conflict is between 'I love humanity' and 'it's hard with an actual person': behind the detachment and rebellion-for-rebellion's-sake hides a fear of dissolving and losing themselves. Maturity for Aquarius is uniting freedom with warmth — seeing that closeness does not cancel individuality, and that changing the world begins with the willingness to be truly present with one living person.",
        ),
    },
    "Pis": {
        "archetype": ("Мистик, Сострадающий", "The Mystic, the Compassionate One"),
        "essence": ("растворение границ, сострадание и связь с высшим", "the dissolving of boundaries, compassion and a link with the higher"),
        "light": ("чуткость, воображение, милосердие и духовная тонкость", "sensitivity, imagination, mercy and spiritual subtlety"),
        "shadow": ("уход от реальности, жертвенность, иллюзии и бегство (в т.ч. в зависимости)", "escape from reality, self-sacrifice, illusions and flight (including into addictions)"),
        "detail": (
            "Рыбы живут без жёстких границ. Они впитывают настроения, как вода — цвет: чувствуют чужую боль как свою, улавливают невидимое, тянутся к тому, что больше обыденности, — к искусству, состраданию, вере. В них редкая мягкость и воображение, способное видеть за краем очевидного.\n\nВ любви они самоотверженны, нежны и всё прощают, растворяются в другом до потери себя, — и оттого легко становятся спасателями или жертвами. В работе сильны там, где нужны сочувствие, творчество, интуиция, помощь, — а жёсткие рамки и грубая конкуренция их ранят. Их внутренний узел — границы: там, где нет своего «нет», реальность давит, и появляется соблазн уплыть — в мечты, идеализацию, а то и в зависимость. Зрелость Рыб — научиться сострадать, не тонув: беречь свою мягкость, но ставить берега, отличать спасение других от бегства от себя и превращать чувствительность в дар, а не в рану.",
            "Pisces live without hard borders. They soak up moods the way water takes color: they feel another's pain as their own, catch the invisible, and reach for what is larger than the everyday — art, compassion, faith. In them is a rare softness and an imagination that sees past the edge of the obvious.\n\nIn love they are selfless, tender and endlessly forgiving, dissolving into another to the point of losing themselves — and so they easily become rescuers or victims. At work they are strong wherever empathy, creativity, intuition and helping are needed, while rigid frames and raw competition wound them. Their inner knot is boundaries: where there is no personal 'no', reality presses in and the temptation arises to swim away — into dreams, idealization, sometimes addiction. Maturity for Pisces is learning to feel with others without drowning: to guard their softness but set shores, to tell rescuing others from fleeing themselves, and to turn sensitivity into a gift rather than a wound.",
        ),
    },
}

_ZODIAC_ORDER = ["Ari", "Tau", "Gem", "Can", "Leo", "Vir", "Lib", "Sco", "Sag", "Cap", "Aqu", "Pis"]


# Знак -> планета, экзальтирующая в нём (инверсия _EXALT). У Близнецов/Льва/Скорпиона/Стрельца/Водолея её нет.
_SIGN_EXALT = {v: k for k, v in _EXALT.items()}
_PLANET_LIST = ["Sun", "Moon", "Mercury", "Venus", "Mars", "Jupiter", "Saturn", "Uranus", "Neptune", "Pluto"]


def _planet_ref(key: str, lang: str) -> dict:
    """Ссылка-планета для интерфейса: ключ, локализованное имя, символ."""
    p = C.POINTS.get(key, {})
    return {"key": key, "name": p.get(lang) or p.get("ru", key), "symbol": p.get("symbol", "")}


def archetypes_list(lang: str = "ru") -> list[dict]:
    out = []
    for code in _ZODIAC_ORDER:
        a = SIGN_ARCHETYPE.get(code, {})
        rulers = [_planet_ref(SIGN_RULER[code], lang)]
        co = MODERN_CORULER.get(code)
        if co:
            rulers.append(_planet_ref(co, lang))
        exalt_key = _SIGN_EXALT.get(code)
        out.append({
            "sign_ru": C.sign_name(code, lang),
            "symbol": C.sign_symbol(code),
            "element": C.sign_element(code, lang),
            "archetype": g(a.get("archetype"), lang) if a.get("archetype") else "",
            "essence": g(a.get("essence"), lang) if a.get("essence") else "",
            "light": g(a.get("light"), lang) if a.get("light") else "",
            "shadow": g(a.get("shadow"), lang) if a.get("shadow") else "",
            "detail": g(a.get("detail"), lang) if a.get("detail") else "",
            "rulers": rulers,
            "exalt": _planet_ref(exalt_key, lang) if exalt_key else None,
        })
    return out


def planets_info(lang: str = "ru") -> dict:
    """Полные описания планет для всплывающего окна: суть + функция/любовь/дело."""
    out = {}
    for key in _PLANET_LIST:
        p = C.POINTS.get(key, {})
        sph = PLANET_SPHERES.get(key, {})
        out[key] = {
            "name": p.get(lang) or p.get("ru", key),
            "symbol": p.get("symbol", ""),
            "role": planet_role(key, lang),
            "function": g(sph.get("function"), lang) if sph.get("function") else "",
            "love": g(sph.get("love"), lang) if sph.get("love") else "",
            "work": g(sph.get("work"), lang) if sph.get("work") else "",
        }
    return out


def _arc(sign: str):
    return SIGN_ARCHETYPE.get(sign, {})


def _af(sign, field, lang):
    a = SIGN_ARCHETYPE.get(sign, {})
    return g(a.get(field), lang) if a.get(field) else ""


def sphere_love(venus_sign, mars_sign, dsc_sign, lang="ru"):
    sv, sm, sd = C.sign_in(venus_sign, lang), C.sign_in(mars_sign, lang), C.sign_in(dsc_sign, lang)
    if lang == "en":
        return (
            f"How you love (Venus {sv}): you express feelings through {_af(venus_sign,'essence',lang)}; "
            f"in closeness you especially value {_af(venus_sign,'light',lang)}. "
            f"Passion and attraction (Mars {sm}): {_af(mars_sign,'essence',lang)}. "
            f"In a partner (Descendant {sd}) you are drawn to {_af(dsc_sign,'essence',lang)}; "
            f"but do not idealize — the shadow side of the attraction is {_af(dsc_sign,'shadow',lang)}."
        )
    return (
        f"Как вы любите (Венера {sv}): проявляете чувства через {_af(venus_sign,'essence',lang)}; "
        f"в близости особенно цените {_af(venus_sign,'light',lang)}. "
        f"Страсть и влечение (Марс {sm}): {_af(mars_sign,'essence',lang)}. "
        f"В партнёре (Десцендент {sd}) вас притягивает {_af(dsc_sign,'essence',lang)}; "
        f"но не идеализируйте — теневая сторона притяжения это {_af(dsc_sign,'shadow',lang)}."
    )


def sphere_career(mc_sign, sun_sign, saturn_sign, lang="ru"):
    smc, ss, ssat = C.sign_in(mc_sign, lang), C.sign_in(sun_sign, lang), C.sign_in(saturn_sign, lang)
    if lang == "en":
        return (
            f"Vocation and public role (MC {smc}): you are led by {_af(mc_sign,'essence',lang)}; "
            f"your strengths in work are {_af(mc_sign,'light',lang)}. "
            f"Inner driver (Sun {ss}): {_af(sun_sign,'essence',lang)}. "
            f"Discipline and career endurance (Saturn {ssat}): {_af(saturn_sign,'light',lang)}; "
            f"the risk zone for growth is {_af(saturn_sign,'shadow',lang)}."
        )
    return (
        f"Призвание и публичная роль (MC {smc}): вас ведёт {_af(mc_sign,'essence',lang)}; "
        f"сильные стороны в деле — {_af(mc_sign,'light',lang)}. "
        f"Внутренний двигатель (Солнце {ss}): {_af(sun_sign,'essence',lang)}. "
        f"Дисциплина и карьерная выдержка (Сатурн {ssat}): {_af(saturn_sign,'light',lang)}; "
        f"зона риска для роста — {_af(saturn_sign,'shadow',lang)}."
    )


def sect_text(is_day: bool, lang: str = "ru") -> str:
    """Карта дня/ночи (sect) — простым языком: кто из планет «добрее», кто резче."""
    if lang == "en":
        if is_day:
            return ("You were born by day — the Sun was above the horizon, so this is a daytime chart. "
                    "In a day chart the main helper is Jupiter (its gifts are especially generous), strict Saturn "
                    "behaves more mildly and constructively, while Mars tends to show up more sharply. "
                    "The leading light of such a chart is the Sun.")
        return ("You were born by night — the Sun was below the horizon, so this is a night-time chart. "
                "In a night chart the main helper is Venus (its gifts are especially warm), forceful Mars "
                "behaves more mildly, while strict Saturn tends to show up more sharply. "
                "The leading light of such a chart is the Moon.")
    if is_day:
        return ("Вы родились днём — Солнце было над горизонтом, это «дневная» карта. "
                "В дневной карте главный помощник — Юпитер (его дары особенно щедры), строгий Сатурн действует "
                "мягче и конструктивнее, а вот Марс проявляется резче. Ведущее светило такой карты — Солнце.")
    return ("Вы родились ночью — Солнце было под горизонтом, это «ночная» карта. "
            "В ночной карте главный помощник — Венера (её дары особенно тёплые), напористый Марс действует "
            "мягче, а строгий Сатурн проявляется резче. Ведущее светило такой карты — Луна.")


def lot_fortune_text(sign: str, house_num, lang: str = "ru") -> str:
    """Жребий (Колесо) Фортуны — точка телесного благополучия, потока и удачи."""
    sign_ru = C.sign_name(sign, lang)
    where = C.house_meaning(house_num, lang).lower() if house_num else ""
    if lang == "en":
        s = (f"The Lot of Fortune (Part of Fortune) marks natural well-being, bodily flow and good luck. "
             f"In {sign_ru}" + (f" and the {house_num}th house" if house_num else "") + ": ")
        s += f"prosperity and a sense of ease come to you most readily through the area of {where}, " if where else ""
        s += f"expressed as {_af(sign, 'essence', lang)}."
        return s
    s = (f"Жребий Фортуны (Колесо Фортуны) — точка естественного благополучия, телесного потока и удачи. "
         f"В знаке {sign_ru}" + (f" и {house_num}-м доме" if house_num else "") + ": ")
    s += f"благополучие и ощущение лёгкости приходят к вам легче всего через сферу «{where}», " if where else ""
    s += f"проявляясь как {_af(sign, 'essence', lang)}."
    return s


def return_forecast(rtype: str, asc_sign: str, sun_house, moon_sign: str, moon_house,
                    asc_natal_house=None, lord_name: str = None, lord_sign: str = None,
                    lord_house=None, lang: str = "ru") -> dict:
    """Тема периода по карте возвращения: главная сфера (наложение Асц соляра на натал),
    управитель года, тон (Асц), фокус (дом Солнца), климат (Луна)."""
    is_solar = str(rtype).lower().startswith("sol")
    a = SIGN_ARCHETYPE.get(asc_sign, {})
    m = SIGN_ARCHETYPE.get(moon_sign, {})
    asc_ru = C.sign_name(asc_sign, lang)
    moon_ru = C.sign_name(moon_sign, lang)
    sun_h_mean = C.house_meaning(sun_house, lang).lower() if sun_house else ""
    sun_h_exp = g(HOUSE_EXP.get(sun_house, ("", "")), lang) if sun_house else ""
    nat_mean = C.house_meaning(asc_natal_house, lang).lower() if asc_natal_house else ""
    nat_exp = g(HOUSE_EXP.get(asc_natal_house, ("", "")), lang) if asc_natal_house else ""
    lord_ru = C.point_name(lord_name, lang) if lord_name else ""
    lord_sign_ru = C.sign_name(lord_sign, lang) if lord_sign else ""
    per = "year" if (is_solar and lang == "en") else ("month" if lang == "en" else ("года" if is_solar else "месяца"))

    if lang == "en":
        out = {
            "overlay": (f"Most important: the return Ascendant falls into your natal {_ord_en(asc_natal_house)} house — so the main theme of the {per} concerns the area of {nat_mean}. {nat_exp}") if asc_natal_house else "",
            "tone": f"The tone of the {per} is set by the return Ascendant in {asc_ru}: {g(a.get('essence',('','')), lang)}. At its best — {g(a.get('light',('','')), lang)}.",
            "focus": f"A key focus is the area of {sun_h_mean} (the Sun sits in the {_ord_en(sun_house)} house of the return). {sun_h_exp}" if sun_house else "",
            "mood": f"Emotional climate — the Moon in {moon_ru}" + (f", {_ord_en(moon_house)} house" if moon_house else "") + f": {g(m.get('essence',('','')), lang)}.",
            "lord": (f"Ruler of the {per} — {lord_ru} (ruler of the return Ascendant), placed in {lord_sign_ru}" + (f", {_ord_en(lord_house)} house" if lord_house else "") + f" of the return; that area gets extra emphasis.") if lord_name else "",
        }
        return out
    ret_w = "соляра" if is_solar else "лунара"
    out = {
        "overlay": (f"Самое важное: Асцендент {ret_w} попал в {asc_natal_house}-й дом вашей натальной карты — значит, главная тема {per} связана со сферой «{nat_mean}». {nat_exp}") if asc_natal_house else "",
        "tone": f"Тон {per} задаёт Асцендент возвращения в знаке {asc_ru}: {g(a.get('essence',('','')), lang)}. В лучшем — {g(a.get('light',('','')), lang)}.",
        "focus": (f"Ещё один акцент — сфера «{sun_h_mean}» (Солнце в {sun_house}-м доме возвращения). {sun_h_exp}") if sun_house else "",
        "mood": f"Эмоциональный климат — Луна в знаке {moon_ru}" + (f", {moon_house}-й дом" if moon_house else "") + f": {g(m.get('essence',('','')), lang)}.",
        "lord": (f"Управитель {per} — {lord_ru} (управитель Асцендента {ret_w}), в карте {ret_w} в знаке {lord_sign_ru}" + (f", {lord_house}-й дом" if lord_house else "") + "; эта сфера получает дополнительный акцент.") if lord_name else "",
    }
    return out


def sphere_health(asc_sign, moon_sign, h6_sign, lang="ru"):
    sa, smo, sh = C.sign_in(asc_sign, lang), C.sign_in(moon_sign, lang), C.sign_in(h6_sign, lang)
    if lang == "en":
        return (
            f"Vitality and the body (Ascendant {sa}): {_af(asc_sign,'essence',lang)}; "
            f"your resource is {_af(asc_sign,'light',lang)}. "
            f"Emotions and psychosomatics (Moon {smo}): you react through {_af(moon_sign,'essence',lang)}, "
            f"and under prolonged stress tension seeks an outlet — watch out for {_af(moon_sign,'shadow',lang)}. "
            f"Care for health and routine (6th house {sh}): an approach through {_af(h6_sign,'essence',lang)} suits you."
        )
    return (
        f"Жизненный тонус и тело (Асцендент {sa}): {_af(asc_sign,'essence',lang)}; "
        f"ваш ресурс — {_af(asc_sign,'light',lang)}. "
        f"Эмоции и психосоматика (Луна {smo}): вы реагируете через {_af(moon_sign,'essence',lang)}, "
        f"и при длительном стрессе напряжение ищет выход — обратите внимание на {_af(moon_sign,'shadow',lang)}. "
        f"Забота о здоровье и режим (6-й дом {sh}): вам подходит подход через {_af(h6_sign,'essence',lang)}."
    )


# --------------------------------------------------------------------------- #
#  Углублённый разбор: ретроградность, фаза Луны, конфигурации, полушария
# --------------------------------------------------------------------------- #
RETROGRADE_NOTE = {
    "Mercury": ("мышление обращено внутрь: вы переосмысливаете информацию по-своему, не сразу высказываетесь, склонны к самостоятельным, нестандартным выводам и повторному обдумыванию.",
                "thinking turns inward: you reprocess information in your own way, don't speak out at once, and tend toward independent, unconventional conclusions and review."),
    "Venus": ("ценности и любовь переживаются внутренне и нестандартно: вы по-своему чувствуете близость, можете не доверять открытым проявлениям чувств и переоценивать отношения изнутри.",
              "values and love are experienced inwardly and unconventionally: you feel closeness in your own way, may distrust open displays of feeling and reassess relationships from within."),
    "Mars": ("энергия направлена внутрь: вы действуете не напрямую, копите импульс, иногда подавляете гнев — важно научиться выражать волю и желания открыто.",
             "energy is directed inward: you act indirectly, build up impulse, sometimes suppress anger — it's important to learn to express will and desire openly."),
    "Jupiter": ("вера и рост идут изнутри: вы ищете собственную философию, развиваетесь через внутренний поиск, а не внешнюю экспансию.",
                "faith and growth come from within: you seek your own philosophy and develop through inner search rather than outer expansion."),
    "Saturn": ("дисциплина и требовательность обращены к себе: усиленная самокритика и уроки о собственной ценности; опора строится изнутри.",
               "discipline and demands turn on yourself: heightened self-criticism and lessons about self-worth; your backbone is built from within."),
    "Uranus": ("бунт и потребность в свободе переживаются внутренне, как личное право быть собой.",
               "rebellion and the need for freedom are experienced inwardly, as a personal right to be yourself."),
    "Neptune": ("духовность и воображение обращены вглубь себя.",
                "spirituality and imagination turn deep inward."),
    "Pluto": ("трансформация идёт через глубокую внутреннюю работу.",
              "transformation goes through deep inner work."),
    "Chiron": ("исцеление раны — внутренний, личный путь.",
               "healing the wound is an inner, personal journey."),
}


def retrograde_note(name, lang="ru"):
    n = RETROGRADE_NOTE.get(name)
    return g(n, lang) if n else ""


LUNAR_PHASE_MEANING = {
    "New Moon": ("Новолуние: вы человек инстинкта и порыва — действуете спонтанно, начинаете новое, смотрите вперёд, не оглядываясь. Субъективность и вера в себя.",
                 "New Moon: you are a person of instinct and impulse — acting spontaneously, starting fresh, looking forward without glancing back. Subjectivity and self-belief."),
    "Waxing Crescent": ("Растущий серп: вы боретесь с инерцией прошлого и строите новое; в вас живёт стремление утвердить свою индивидуальность.",
                        "Waxing Crescent: you struggle with the inertia of the past and build something new; a drive to assert your individuality lives in you."),
    "First Quarter": ("Первая четверть: вы — деятель и строитель, решительный и волевой; растёте через кризисы действия и преодоление.",
                      "First Quarter: you are a doer and builder, decisive and strong-willed; you grow through crises of action and overcoming."),
    "Waxing Gibbous": ("Растущая Луна: вы стремитесь к совершенству и смыслу, анализируете, доводите до ума — вам важно понимать «зачем».",
                       "Waxing Gibbous: you strive for perfection and meaning, analyse and refine — it matters to you to understand the “why”."),
    "Full Moon": ("Полнолуние: вы осознанны и объективны, реализуетесь через отношения и партнёрство; важен баланс себя и другого.",
                  "Full Moon: you are aware and objective, fulfilled through relationships and partnership; the balance of self and other matters."),
    "Waning Gibbous": ("Убывающая Луна: вы делитесь, учите и передаёте опыт; вам важно донести понятое до других.",
                       "Waning (Disseminating) Moon: you share, teach and pass on experience; conveying what you've understood matters to you."),
    "Last Quarter": ("Последняя четверть: вы переориентируетесь, переосмысливаете убеждения; внутренний кризис сознания ведёт к перестройке.",
                     "Last Quarter: you reorient and rethink beliefs; an inner crisis of consciousness leads to restructuring."),
    "Waning Crescent": ("Убывающий серп (бальзамическая Луна): вы визионер с сильной кармической памятью; завершаете циклы и сеете семена будущего.",
                        "Balsamic Moon: you are a visionary with strong karmic memory; you close cycles and plant the seeds of the future."),
}


def lunar_phase_meaning(phase_name, lang="ru"):
    m = LUNAR_PHASE_MEANING.get(phase_name)
    return g(m, lang) if m else ""


# Энергия дня по фазе Луны — что благоприятно делать (для лунного календаря).
LUNAR_PHASE_ADVICE = {
    "New Moon": ("Время начинаний: загадывайте цели и «сажайте семена» новых дел. Энергии пока мало — не перегружайтесь, наметьте планы.",
                 "A time for beginnings: set intentions and plant the seeds of new undertakings. Energy is still low — don't overload, sketch out plans."),
    "Waxing Crescent": ("Первые шаги к задуманному: набирайте обороты, преодолевайте сомнения и инерцию, укрепляйте начатое.",
                        "First steps toward your aim: gather momentum, overcome doubt and inertia, reinforce what you've begun."),
    "First Quarter": ("Точка действия: решайте задачи, преодолевайте препятствия, проявляйте волю. Возможно напряжение — оно толкает вперёд.",
                      "A point of action: tackle tasks, push through obstacles, exert your will. Tension may rise — it drives you forward."),
    "Waxing Gibbous": ("Доработка и доводка: уточняйте детали, исправляйте, готовьтесь к результату. Хорошо для анализа и улучшений.",
                       "Refinement and fine-tuning: polish details, correct, prepare for the result. Good for analysis and improvement."),
    "Full Moon": ("Кульминация и результаты: пик энергии и эмоций, ясность — но и обострение. Хорошо завершать, подводить итоги и праздновать.",
                  "Culmination and results: a peak of energy and emotion, clarity — but also heightened sensitivity. Good for completing, summing up and celebrating."),
    "Waning Gibbous": ("Время делиться и отдавать: благодарность, обучение других, осмысление достигнутого, наведение порядка в делах.",
                       "A time to share and give back: gratitude, teaching others, making sense of what's achieved, tidying up affairs."),
    "Last Quarter": ("Расчистка и пересмотр: отпускайте лишнее, завершайте начатое, исправляйте ошибки, освобождайте место новому.",
                     "Clearing and review: let go of the excess, finish what's begun, correct mistakes, make room for the new."),
    "Waning Crescent": ("Отдых и восстановление: замедлитесь, отпустите старое, копите силы и подводите итог цикла перед новолунием.",
                        "Rest and recovery: slow down, release the old, gather strength and close the cycle before the new moon."),
}

# Эмоциональная «погода» дня по знаку, в котором идёт Луна.
MOON_IN_SIGN_MOOD = {
    "Ari": ("День энергичный и импульсивный — тянет действовать, начинать и лидировать; легко вспылить, держите темп под контролем.",
            "An energetic, impulsive day — you're drawn to act, start and lead; tempers flare easily, keep the pace in check."),
    "Tau": ("День спокойный и чувственный — хочется уюта, вкусной еды и надёжности; спешка и перемены сегодня в тягость.",
            "A calm, sensual day — you crave comfort, good food and stability; haste and change feel burdensome."),
    "Gem": ("День лёгкий и общительный — много контактов, информации и поездок; внимание скачет, трудно сосредоточиться.",
            "A light, sociable day — lots of contacts, information and short trips; attention darts about, hard to focus."),
    "Can": ("День чувствительный и домашний — тянет к близким, заботе и уюту; выше ранимость и потребность в безопасности.",
            "A sensitive, homey day — you're drawn to loved ones, care and comfort; sensitivity and the need for safety rise."),
    "Leo": ("День яркий и щедрый — хочется творить, блистать и получать признание; берегитесь гордости и жажды внимания.",
            "A bright, generous day — you want to create, shine and be recognised; watch for pride and a craving for attention."),
    "Vir": ("День практичный и аккуратный — удобно наводить порядок, заниматься делами, здоровьем и мелочами; не придирайтесь к себе.",
            "A practical, tidy day — good for order, chores, health and details; don't be too hard on yourself."),
    "Lib": ("День про отношения и красоту — тянет к гармонии, партнёрству и эстетике; решения даются трудно, хочется компромисса.",
            "A day of relationships and beauty — drawn to harmony, partnership and aesthetics; decisions come hard, you seek compromise."),
    "Sco": ("День глубокий и напряжённый — сильные чувства и тяга к самой сути; возможны страсти, ревность и желание контроля.",
            "A deep, intense day — strong feelings and a pull toward the core; passion, jealousy and a wish for control may surface."),
    "Sag": ("День оптимистичный и свободный — тянет к путешествиям, учёбе, простору и приключениям; не разбрасывайтесь обещаниями.",
            "An optimistic, free day — drawn to travel, study, open space and adventure; don't scatter promises."),
    "Cap": ("День деловой и собранный — удобно работать, строить планы и брать ответственность; следите, чтобы не зачерстветь.",
            "A businesslike, focused day — good for work, planning and taking responsibility; just don't turn cold."),
    "Aqu": ("День необычный и независимый — тянет к новому, дружбе и идеям; нужна свобода, рутина раздражает.",
            "An unusual, independent day — drawn to the new, to friendship and ideas; you need freedom, routine irritates."),
    "Pis": ("День мечтательный и чуткий — обострены интуиция и сочувствие; хочется уединения, творчества и покоя.",
            "A dreamy, sensitive day — intuition and compassion are heightened; you long for solitude, creativity and peace."),
}


def lunar_phase_advice(phase_name, lang="ru"):
    m = LUNAR_PHASE_ADVICE.get(phase_name)
    return g(m, lang) if m else ""


def moon_sign_mood(sign, lang="ru"):
    m = MOON_IN_SIGN_MOOD.get(sign)
    return g(m, lang) if m else ""


PATTERN_INFO = {
    "grand_trine": {
        "name": ("Большой трин", "Grand Trine"),
        "text": ("замкнутый треугольник из трёх гармоничных тринов — мощный природный дар и лёгкость в стихии этих планет. Талант даётся даром, но именно поэтому его легко не реализовать: нужен внешний стимул, чтобы не закоснеть в зоне комфорта.",
                 "a closed triangle of three harmonious trines — a powerful natural gift and ease in the element of these planets. The talent comes for free, which is exactly why it can go unused: an outer stimulus is needed so it doesn't stagnate in the comfort zone."),
    },
    "t_square": {
        "name": ("Тау-квадрат", "T-Square"),
        "text": ("две планеты в оппозиции, обе в квадрате к третьей (вершине). Это мощный двигатель: напряжение требует постоянного действия. Планета-вершина — точка наибольшего давления и одновременно ключ к разрядке через осознанную работу.",
                 "two planets in opposition, both square a third (the apex). A powerful engine: the tension demands constant action. The apex planet is the point of greatest pressure and at the same time the key to release through conscious work."),
    },
    "grand_cross": {
        "name": ("Большой крест", "Grand Cross"),
        "text": ("четыре планеты в двух оппозициях и четырёх квадратах — максимальное напряжение по всем четырём точкам. Колоссальная внутренняя сила и выносливость, но энергия рассеивается; мастерство приходит через умение удерживать равновесие.",
                 "four planets in two oppositions and four squares — maximum tension across all four points. Colossal inner strength and endurance, but the energy scatters; mastery comes through the ability to hold balance."),
    },
    "yod": {
        "name": ("Йод (Перст судьбы)", "Yod (Finger of God)"),
        "text": ("две планеты в секстиле, обе в квинконсе к третьей (вершине). Особая «миссия» и судьбоносная тема: планета-вершина требует постоянной тонкой подстройки и часто связана с поворотными моментами жизни.",
                 "two planets in sextile, both quincunx a third (the apex). A special “mission” and fateful theme: the apex planet demands constant fine adjustment and is often tied to life's turning points."),
    },
    "stellium": {
        "name": ("Стеллиум", "Stellium"),
        "text": ("скопление трёх и более планет в одном знаке или доме — мощная концентрация энергии. Эта сфера жизни и качества знака резко доминируют в характере, становясь и главной силой, и зоной перекоса.",
                 "a cluster of three or more planets in one sign or house — a powerful concentration of energy. This life area and the sign's qualities strongly dominate the character, becoming both the main strength and a point of imbalance."),
    },
}


def pattern_name(key, lang="ru"):
    p = PATTERN_INFO.get(key)
    return g(p["name"], lang) if p else key


def pattern_text(key, lang="ru"):
    p = PATTERN_INFO.get(key)
    return g(p["text"], lang) if p else ""


HEMISPHERE_INFO = {
    "lower": ("Нижнее полушарие (дома 1–6) акцентировано: жизнь разворачивается прежде всего в личном, внутреннем пространстве — вы субъективны, опираетесь на себя и свой внутренний мир.",
              "The lower hemisphere (houses 1–6) is emphasized: life unfolds mainly in the personal, inner space — you are subjective and rely on yourself and your inner world."),
    "upper": ("Верхнее полушарие (дома 7–12) акцентировано: вы устремлены вовне, в социальную и публичную сферу — реализуетесь через мир, других людей и общественные цели.",
              "The upper hemisphere (houses 7–12) is emphasized: you are oriented outward, toward the social and public sphere — fulfilled through the world, other people and collective goals."),
    "east": ("Восточное полушарие (вокруг Асцендента) акцентировано: вы инициатор, опираетесь на свободу воли и сами творите обстоятельства.",
             "The eastern hemisphere (around the Ascendant) is emphasized: you are an initiator, relying on free will and shaping your own circumstances."),
    "west": ("Западное полушарие (вокруг Десцендента) акцентировано: ваша жизнь во многом разворачивается через отношения и других людей, через отклик и обстоятельства.",
             "The western hemisphere (around the Descendant) is emphasized: your life largely unfolds through relationships and other people, through response and circumstance."),
}


def hemisphere_text(key, lang="ru"):
    h = HEMISPHERE_INFO.get(key)
    return g(h, lang) if h else ""


def luminary_info(which: str, sign: str, lang: str = "ru") -> Optional[dict]:
    lum = LUMINARY_PURPOSE.get(which)
    arc = SIGN_ARCHETYPE.get(sign)
    if not lum or not arc:
        return None
    lum_l = lum["en"] if lang == "en" else lum["ru"]
    sign_ru = C.sign_name(sign, lang)
    archetype = g(arc["archetype"], lang)
    essence = g(arc["essence"], lang)
    light = g(arc["light"], lang)
    shadow = g(arc["shadow"], lang)
    if lang == "en":
        text = (
            f"{lum_l[0]} is {lum_l[1]}. "
            f"In {sign_ru} (archetype “{archetype}”: {essence}) this unfolds as follows. "
            f"At its best — {light}. In the shadow — {shadow}."
        )
    else:
        text = (
            f"{lum_l[0]} — это {lum_l[1]}. "
            f"В знаке {sign_ru} (архетип «{archetype}»: {essence}) "
            f"это раскрывается так. В сильном проявлении — {light}. "
            f"В теневом — {shadow}."
        )
    return {"purpose": lum_l[1], "sign_ru": sign_ru, "archetype": archetype, "text": text}
