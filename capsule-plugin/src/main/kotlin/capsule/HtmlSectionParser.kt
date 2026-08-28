package capsule

object HtmlSectionParser {

    private val sectionOpenRegex = Regex("""<section\b(?![^>]*/>)[^>]*>""")
    private val sectionCloseRegex = Regex("""</section>""")

    /**
     * Gourmand à dessein : `.*` et non `.*?`.
     *
     * La forme paresseuse s'arrête au premier `</div>` rencontré, donc au
     * premier `<div>` imbriqué dans une diapo — colonnes, encadrés, tout deck
     * un peu construit en contient. Le contenu était alors tronqué en silence :
     * la moitié des sections disparaissait du compte, et l'appelant croyait
     * simplement que le deck avait moins de diapos. Le `</div>` du conteneur
     * `slides` est le dernier du document, d'où la forme gourmande.
     */
    private val slidesDivRegex = Regex("""(?s)<div class="slides">(.*)</div>""")

    /**
     * Contenu du conteneur `<div class="slides">` d'un deck reveal.js.
     *
     * @return le balisage entre les balises, ou `null` si le deck n'a pas de
     *         conteneur `slides`.
     */
    fun slidesMarkup(deckHtml: String): String? =
        slidesDivRegex.find(deckHtml)?.groupValues?.get(1)

    private val headRegex = Regex("""(?s)<head>.*?</head>""")

    /**
     * Bloc `<head>` du deck, feuilles de style comprises.
     *
     * Toute diapo extraite le rejoue tel quel : c'est ce qui lui garde son
     * thème. Trois endroits en avaient chacun leur copie, dont deux
     * recompilaient l'expression à chaque diapo.
     *
     * @return le bloc `<head>…</head>`, ou la chaîne vide s'il n'y en a pas.
     */
    fun headMarkup(deckHtml: String): String = headRegex.find(deckHtml)?.value.orEmpty()

    fun extractTopLevelSections(slidesContent: String): List<String> {
        val sections = mutableListOf<String>()
        var depth = 0
        var currentStart = -1
        var pos = 0

        while (pos < slidesContent.length) {
            val openMatch = sectionOpenRegex.find(slidesContent, pos)
            val closeMatch = sectionCloseRegex.find(slidesContent, pos)

            val nextOpen = openMatch?.range?.first ?: Int.MAX_VALUE
            val nextClose = closeMatch?.range?.first ?: Int.MAX_VALUE

            if (nextOpen < nextClose && openMatch != null) {
                if (depth == 0) {
                    currentStart = openMatch.range.first
                }
                depth++
                pos = openMatch.range.last + 1
            } else if (closeMatch != null) {
                depth--
                if (depth == 0 && currentStart >= 0) {
                    sections.add(slidesContent.substring(currentStart, closeMatch.range.last + 1))
                    currentStart = -1
                }
                pos = closeMatch.range.last + 1
            } else {
                break
            }
        }

        return sections
    }

    /**
     * Finds the opening tags of top-level `<section>` elements, skipping nested
     * sections (vertical stacks) and self-closing tags.
     *
     * Unlike [extractTopLevelSections] which returns full section content, this
     * returns only the opening tag matches (with their ranges), enabling callers
     * to inject attributes (e.g. `data-audio`) at the correct position without
     * re-parsing HTML with fragile raw regexes (CR-9).
     *
     * @param html the HTML content to scan
     * @return list of [MatchResult] for each top-level `<section ...>` open tag,
     *         ordered by appearance
     */
    fun findTopLevelSectionOpenTags(html: String): List<MatchResult> {
        val tags = mutableListOf<MatchResult>()
        var depth = 0
        var pos = 0

        while (pos < html.length) {
            val openMatch = sectionOpenRegex.find(html, pos)
            val closeMatch = sectionCloseRegex.find(html, pos)

            val nextOpen = openMatch?.range?.first ?: Int.MAX_VALUE
            val nextClose = closeMatch?.range?.first ?: Int.MAX_VALUE

            if (nextOpen < nextClose && openMatch != null) {
                if (depth == 0) {
                    tags.add(openMatch)
                }
                depth++
                pos = openMatch.range.last + 1
            } else if (closeMatch != null) {
                depth--
                pos = closeMatch.range.last + 1
            } else {
                break
            }
        }

        return tags
    }
}
