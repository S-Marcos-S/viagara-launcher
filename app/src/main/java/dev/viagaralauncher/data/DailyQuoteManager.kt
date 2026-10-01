// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.data

import java.util.Calendar

data class DailyQuote(
    val quote: String,
    val author: String,
)

/**
 * Provedor de frases diárias de reflexão e motivação com curadoria temática
 * baseada nos dias da semana:
 * - Segunda-feira: Começo, coragem, iniciativa, foco e novos começos.
 * - Terça-feira: Disciplina, consistência, constância e hábitos de longo prazo.
 * - Quarta-feira: Meio de jornada, equilíbrio, resiliência e clareza mental.
 * - Quinta-feira: Perseverança, sabedoria, aprendizado e superação.
 * - Sexta-feira: Gratidão, contentamento, conclusão de ciclos e leveza.
 * - Sábado: Desaceleração, presença, descanso ativo e contemplação.
 * - Domingo: Paz interior, serenidade profunda, renovação e esperança.
 */
object DailyQuoteManager {

    fun getQuoteForToday(calendar: Calendar = Calendar.getInstance()): DailyQuote {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val quotes = getQuotesForDayOfWeek(dayOfWeek)
        val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
        val year = calendar.get(Calendar.YEAR)
        val index = ((weekOfYear + year) % quotes.size).let { if (it < 0) -it else it }
        return quotes[index]
    }

    fun getQuotesForDayOfWeek(dayOfWeek: Int): List<DailyQuote> = when (dayOfWeek) {
        Calendar.MONDAY -> mondayQuotes
        Calendar.TUESDAY -> tuesdayQuotes
        Calendar.WEDNESDAY -> wednesdayQuotes
        Calendar.THURSDAY -> thursdayQuotes
        Calendar.FRIDAY -> fridayQuotes
        Calendar.SATURDAY -> saturdayQuotes
        Calendar.SUNDAY -> sundayQuotes
        else -> mondayQuotes
    }

    // Segunda-feira: Foco, coragem, recomeço e determinação
    private val mondayQuotes = listOf(
        DailyQuote("O segredo de progredir é começar.", "Mark Twain"),
        DailyQuote("A cada nova manhã nasce uma nova oportunidade para recomeçar.", "Sêneca"),
        DailyQuote("Comece de onde você está. Use o que você tem. Faça o que você pode.", "Arthur Ashe"),
        DailyQuote("A coragem não é a ausência do medo, mas a decisão de que algo é mais importante do que o medo.", "Ambrose Redmoon"),
        DailyQuote("Você não precisa ser ótimo para começar, mas precisa começar para ser ótimo.", "Zig Ziglar"),
        DailyQuote("A melhor maneira de prever o futuro é criá-lo.", "Peter Drucker"),
        DailyQuote("Todo recomeço traz a força de um novo capítulo em branco.", "Machado de Assis"),
        DailyQuote("Não espere por circunstâncias ideais; tome uma decisão e torne as circunstâncias ideais.", "George Bernard Shaw"),
        DailyQuote("O primeiro passo não te leva onde você quer ir, mas te tira de onde você está.", "Provérbio"),
        DailyQuote("A persistência é o caminho do êxito.", "Charles Chaplin"),
        DailyQuote("Hoje é um novo dia, com novas forças e novas decisões pela frente.", "Eleanor Roosevelt"),
        DailyQuote("A energia que você coloca no início define o ritmo de toda a jornada.", "Marco Aurélio"),
        DailyQuote("Não tenha medo de dar passos ousados quando a direção é clara.", "John D. Rockefeller"),
        DailyQuote("A verdadeira força se desenvolve na superação das primeiras resistências.", "Arnold Schwarzenegger"),
    )

    // Terça-feira: Disciplina, constância, execução prática e hábitos
    private val tuesdayQuotes = listOf(
        DailyQuote("Nós somos aquilo que fazemos repetidamente. A excelência, portanto, não é um ato, mas um hábito.", "Aristóteles"),
        DailyQuote("Pequenas disciplinas repetidas com consistência todos os dias levam a grandes conquistas.", "John C. Maxwell"),
        DailyQuote("A disciplina é a ponte entre metas e realizações.", "Jim Rohn"),
        DailyQuote("Não é a força, mas a constância dos sentimentos que conduz os homens à realização.", "Friedrich Nietzsche"),
        DailyQuote("Concentre todos os seus pensamentos na tarefa que está em suas mãos.", "Alexander Graham Bell"),
        DailyQuote("O sucesso é a soma de pequenos esforços repetidos dia após dia.", "Robert Collier"),
        DailyQuote("A paciência e a persistência têm o efeito mágico de fazer as dificuldades desaparecerem.", "John Quincy Adams"),
        DailyQuote("A motivação faz você começar, mas a disciplina faz você continuar.", "Jim Ryun"),
        DailyQuote("Faça o que precisa ser feito hoje para que o amanhã encontre um terreno mais fértil.", "Sêneca"),
        DailyQuote("O hábito de persistir com serenidade constrói resultados duradouros.", "Herbert Kaufman"),
        DailyQuote("Grandes realizações requerem dedicação construída passo a passo.", "Heráclito"),
        DailyQuote("Mantenha o foco: a clareza de propósito dissipa qualquer distração.", "Marco Aurélio"),
        DailyQuote("Quem move montanhas começa carregando pequenas pedras.", "Confúcio"),
        DailyQuote("A ação bem direcionada é a única resposta necessária às dúvidas.", "Johann Wolfgang von Goethe"),
    )

    // Quarta-feira: Equilíbrio, resiliência, superação do cansaço e clareza mental
    private val wednesdayQuotes = listOf(
        DailyQuote("No meio da dificuldade encontra-se a oportunidade.", "Albert Einstein"),
        DailyQuote("Quem tem um porquê para viver pode suportar quase qualquer como.", "Friedrich Nietzsche"),
        DailyQuote("A mente calma traz clareza para enxergar o melhor caminho em meio ao caos.", "Sêneca"),
        DailyQuote("Mantenha seus olhos nas estrelas e seus pés no chão.", "Theodore Roosevelt"),
        DailyQuote("Não olhe para o quanto ainda falta caminhar; valorize o quanto você já avançou.", "Provérbio"),
        DailyQuote("O meio da jornada exige paciência: a semente trabalha no silêncio antes de florescer.", "Lao Tsé"),
        DailyQuote("A árvore com raízes profundas não teme o vento forte.", "Provérbio Oriental"),
        DailyQuote("Dificuldades preparam pessoas comuns para destinos extraordinários.", "C.S. Lewis"),
        DailyQuote("A sabedoria consiste em saber o que ignorar.", "William James"),
        DailyQuote("Respire fundo e mantenha o ritmo: a metade do caminho já é uma grande vitória.", "Epicteto"),
        DailyQuote("A vida é feita de equilíbrio: nem tudo é urgência, nem tudo é pausa.", "Guimarães Rosa"),
        DailyQuote("A calma diante do desafio é a marca da verdadeira força interior.", "Marco Aurélio"),
        DailyQuote("Mesmo nos dias mais longos, lembre-se de que cada esforço tem seu propósito.", "Viktor Frankl"),
        DailyQuote("Quando o vento muda, ajustamos as velas sem perder o rumo.", "Provérbio"),
    )

    // Quinta-feira: Perseverança, sabedoria, aprendizado e foco na reta final
    private val thursdayQuotes = listOf(
        DailyQuote("A perseverança é a mãe da boa sorte.", "Miguel de Cervantes"),
        DailyQuote("Não importa o quão devagar você vá, desde que você não pare.", "Confúcio"),
        DailyQuote("A vitória pertence àquele que persiste até o fim.", "Napoleão Bonaparte"),
        DailyQuote("A sabedoria da vida não está em nunca cair, mas em levantar-se a cada queda.", "Nelson Mandela"),
        DailyQuote("Grandes coisas não são feitas por impulso, mas pela soma de pequenas coisas.", "Vincent van Gogh"),
        DailyQuote("Tudo o que você busca com sinceridade também está buscando você.", "Rumi"),
        DailyQuote("Confie no processo: cada esforço de hoje constrói a colheita do amanhã.", "Ralph Waldo Emerson"),
        DailyQuote("A reta final exige menos força bruta e mais foco e serenidade.", "Sêneca"),
        DailyQuote("A persistência transforma o esforço silencioso em conquista visível.", "Provérbio"),
        DailyQuote("Nunca desista de um objetivo importante apenas pelo tempo que levará para realizá-lo.", "Earl Nightingale"),
        DailyQuote("A coragem de seguir em frente quando o cansaço surge define o resultado final.", "Winston Churchill"),
        DailyQuote("O aprendizado de cada etapa é o maior tesouro de qualquer caminhada.", "Platão"),
        DailyQuote("Mantenha a fé no destino e a firmeza nos passos presentes.", "Fernando Pessoa"),
        DailyQuote("A água cava a rocha não pela força, mas pela perseverança de cair sempre.", "Ovídio"),
    )

    // Sexta-feira: Gratidão, contentamento, conclusão de ciclos e leveza
    private val fridayQuotes = listOf(
        DailyQuote("A gratidão é a memória do coração.", "Jean-Baptiste Massieu"),
        DailyQuote("A verdadeira felicidade é aproveitar o presente, sem ansiosa dependência do futuro.", "Sêneca"),
        DailyQuote("Aproveite as pequenas coisas; um dia você perceberá que elas eram grandes.", "Robert Brault"),
        DailyQuote("Viver é a coisa mais rara do mundo. A maioria das pessoas apenas existe.", "Oscar Wilde"),
        DailyQuote("Olhe para a semana que passou com gratidão e para os próximos dias com serenidade.", "Marco Aurélio"),
        DailyQuote("Que a paz interior seja o seu maior troféu ao encerrar os compromissos de hoje.", "Epicteto"),
        DailyQuote("A simplicidade é o último grau de sofisticação.", "Leonardo da Vinci"),
        DailyQuote("Celebre o caminho percorrido: a gratidão transforma o que temos em suficiente.", "Melody Beattie"),
        DailyQuote("A leveza de espírito é um presente que você concede a si mesmo.", "Clarice Lispector"),
        DailyQuote("Mais do que cumprir tarefas, o valor da vida está em manter o coração em paz.", "Mário Quintana"),
        DailyQuote("A alegria de uma missão cumprida é o melhor prelúdio para o merecido descanso.", "Johann Wolfgang von Goethe"),
        DailyQuote("Agradeça pelo que foi superado e abra espaço para a calma que chega.", "Guimarães Rosa"),
        DailyQuote("Deixe para trás o que pesou e guarde apenas o que ensinou.", "Provérbio"),
        DailyQuote("O descanso é mais doce quando sabemos que demos o nosso melhor.", "Aristóteles"),
    )

    // Sábado: Desaceleração, presença, descanso ativo e contemplação
    private val saturdayQuotes = listOf(
        DailyQuote("A pressa é inimiga da perfeição e do encantamento.", "Mário Quintana"),
        DailyQuote("Descansar não é perder tempo; é nutrir a alma para continuar vivendo com sentido.", "Sêneca"),
        DailyQuote("A natureza não tem pressa, e mesmo assim tudo é realizado no tempo certo.", "Lao Tsé"),
        DailyQuote("O tempo que você gosta de perder não é tempo perdido.", "Bertrand Russell"),
        DailyQuote("Esteja presente em cada momento, pois a vida só acontece agora.", "Thich Nhat Hanh"),
        DailyQuote("Desacelere o passo para que sua alma consiga alcançar o seu corpo.", "Provérbio Indígena"),
        DailyQuote("A verdadeira riqueza é o tempo livre para contemplar a beleza ao nosso redor.", "Epicuro"),
        DailyQuote("Permita-se viver sem relógio e saborear o que não tem preço.", "Rubem Alves"),
        DailyQuote("A arte de viver reside na capacidade de desfrutar o presente em paz.", "Henry David Thoreau"),
        DailyQuote("Hoje, troque a urgência pelo prazer das coisas simples e verdadeiras.", "Fernando Pessoa"),
        DailyQuote("A vida é o que acontece enquanto estamos ocupados fazendo outros planos.", "John Lennon"),
        DailyQuote("Abra espaço para a criatividade e para o ócio fecundo.", "Domenico De Masi"),
        DailyQuote("O afeto e a presença são os maiores presentes que podemos oferecer a quem amamos.", "Clarice Lispector"),
        DailyQuote("Contemplar a beleza do dia é o melhor remédio para a alma.", "Ralph Waldo Emerson"),
    )

    // Domingo: Paz interior, serenidade profunda, renovação e esperança
    private val sundayQuotes = listOf(
        DailyQuote("A paz vem de dentro de você. Não a procure à sua volta.", "Buda"),
        DailyQuote("Há momentos em que o silêncio é a mais sábia e acolhedora das respostas.", "Henri Nouwen"),
        DailyQuote("A quietude é o solo fértil onde a mente recupera sua verdadeira força.", "Marco Aurélio"),
        DailyQuote("Recolha-se em silêncio e renove suas esperanças para os dias que virão.", "Sêneca"),
        DailyQuote("A serenidade não é a ausência de tempestade, mas a paz no meio dela.", "Santo Agostinho"),
        DailyQuote("O domingo é o descanso do corpo e a preparação gentil da alma para uma nova jornada.", "Provérbio"),
        DailyQuote("Dê a si mesmo o presente da calma e do silêncio interior.", "Eckhart Tolle"),
        DailyQuote("Não carregue o peso do passado nem a ansiedade do amanhã: apenas respire em paz hoje.", "Thich Nhat Hanh"),
        DailyQuote("A esperança é o sonho do homem acordado.", "Aristóteles"),
        DailyQuote("Cuide de si mesmo com a mesma dedicação com que cuida de suas metas.", "Carl Jung"),
        DailyQuote("No silêncio encontramos as respostas que o barulho do mundo esconde.", "Khalil Gibran"),
        DailyQuote("A gratidão pelo descanso de hoje é a bênção que ilumina a semana que vem.", "Guimarães Rosa"),
        DailyQuote("Cultive a paz interior como o seu bem mais precioso.", "Dalai Lama"),
        DailyQuote("Que a sua alma se reabasteça de luz, leveza e serenidade neste dia.", "Cora Coralina"),
    )
}
