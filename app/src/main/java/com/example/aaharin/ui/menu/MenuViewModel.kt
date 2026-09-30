package com.example.aaharin.ui.menu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aaharin.data.Category
import com.example.aaharin.data.MenuItem
import com.example.aaharin.data.ApiMenuItem
import com.example.aaharin.data.ItemListRequest
import com.example.aaharin.network.NetworkModule
import com.example.aaharin.util.coerceToBoolean
import com.example.aaharin.util.coerceToDouble
import com.example.aaharin.util.coerceToInt
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

class MenuViewModel : ViewModel() {

    private companion object {
        /** Reflective TypeToken lookup is not free — resolve it once for the process. */
        val ITEM_LIST_TYPE: Type = object : TypeToken<List<ApiMenuItem>>() {}.type

        /** Prices outside this range are treated as bad rows by the existing filter. */
        const val MIN_VALID_PRICE = 0.0
        const val MAX_VALID_PRICE = 5000.0
    }

    var categories by mutableStateOf<List<Category>>(emptyList())
    var items by mutableStateOf<List<MenuItem>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    /** A search re-fetch, as opposed to the first load - the list stays on screen for it. */
    var isSearching by mutableStateOf(false)
        private set

    /**
     * The query [items] were fetched with.
     *
     * The screen needs this to know the list in front of it is already server-filtered,
     * so it does not re-filter by name and throw away the item-code matches.
     */
    var appliedSearch by mutableStateOf("")
        private set

    /**
     * Loads the menu, optionally narrowed to [itemSearch].
     *
     * The search runs on the server because it matches `item_code` as well as the name,
     * and the code is not part of the menu payload - there is nothing to match on here.
     */
    @Suppress("UNUSED_PARAMETER")
    fun fetchMenu(accessToken: String, restaurantId: Int, tableId: Int, itemSearch: String = "") {
        val query = itemSearch.trim()
        // Keep the current list visible while a search reloads it; only the first load
        // gets the full-screen spinner, otherwise the grid blanks out on every keystroke.
        val isRefetch = items.isNotEmpty()
        if (isRefetch) isSearching = true else isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getItemList(
                    headerToken = accessToken,
                    queryToken = accessToken,
                    // restaurantId is not sent - see [ItemListRequest]; the server reads
                    // it off the access token, which is what keeps the call authenticated.
                    request = ItemListRequest(
                        accessToken = accessToken,
                        tableId = tableId,
                        itemSearch = query
                    )
                )

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    val dataElement = apiResponse?.data

                    if (apiResponse?.code == 200 && dataElement != null) {
                        // Tree-walking, mapping, grouping and sorting a full menu is CPU work.
                        // Run it off the main thread so the list doesn't jank while loading.
                        val parsed = withContext(Dispatchers.Default) {
                            val allApiItems = mutableListOf<ApiMenuItem>()

                            if (dataElement.isJsonObject) {
                                val dataObj = dataElement.asJsonObject
                                val tableData = if (dataObj.has("tableData")) {
                                    dataObj.get("tableData").asJsonObject
                                } else {
                                    dataObj
                                }

                                tableData.entrySet().forEach { entry ->
                                    val key = entry.key
                                    if (key == "menuItems") return@forEach

                                    try {
                                        if (key == "favouriteItems") {
                                            entry.value.asJsonObject.entrySet().forEach { favEntry ->
                                                parseCategory(favEntry.value.asJsonObject, allApiItems)
                                            }
                                        } else {
                                            parseCategory(entry.value.asJsonObject, allApiItems)
                                        }
                                    } catch (_: Exception) {
                                        // Malformed category block — skip it, keep the rest of the menu.
                                    }
                                }
                            }

                            if (allApiItems.isEmpty()) null else buildMenu(allApiItems)
                        }

                        appliedSearch = query
                        if (parsed == null) {
                            // An empty result is the normal answer to a search that matched
                            // nothing, so say that rather than "the menu is empty".
                            items = emptyList()
                            errorMessage = if (query.isEmpty()) {
                                "No items found in the menu."
                            } else {
                                "No item matches \"$query\"."
                            }
                        } else {
                            categories = parsed.categories
                            items = parsed.items
                        }
                    } else {
                        errorMessage = apiResponse?.errorMessage ?: "Failed to load menu"
                    }
                } else {
                    errorMessage = "Server error: ${response.code()}"
                }
            } catch (e: Exception) {
                errorMessage = "Connection error: ${e.localizedMessage}"
            } finally {
                isLoading = false
                isSearching = false
            }
        }
    }

    /** Result of parsing one `item-list` payload. */
    private data class ParsedMenu(val categories: List<Category>, val items: List<MenuItem>)

    /**
     * Flattens, de-duplicates and groups the raw API rows.
     * Extracted from [fetchMenu] so the parse can run off the main thread;
     * the transformation steps are unchanged.
     */
    private fun buildMenu(allApiItems: List<ApiMenuItem>): ParsedMenu {
        // 1. Map all items to a flat list first
        val allMapped = allApiItems
            .asSequence()
            .filter { it.itemName != null }
            .map { apiItem ->
                val name = apiItem.itemName!!.trim()
                // The server switches half off on variation parents and resolves the price
                // (fixed, item % or category %); a missing or zero price means no half.
                val halfPrice = apiItem.halfPrice.coerceToDouble().takeIf { it > 0.0 }
                val hasHalf = apiItem.allowHalf.coerceToBoolean() && halfPrice != null
                MenuItem(
                    id = apiItem.id.coerceToInt(),
                    name = name,
                    price = apiItem.itemPrice.coerceToDouble(),
                    categoryId = apiItem.categoryId.coerceToInt(),
                    isVeg = isVegType(apiItem.itemType),
                    isFavourite = apiItem.isFavourite.coerceToBoolean(),
                    isInclusiveTax = apiItem.isInclusiveTax.coerceToBoolean(),
                    hasHalfOption = hasHalf,
                    halfPrice = if (hasHalf) halfPrice else null,
                    imageUrl = absoluteImageUrl(apiItem.itemImg),
                    // The payload has carried these all along; the app never read them,
                    // so a parent item went onto orders as itself. The web POS only ever
                    // sends one of the children.
                    variants = parseVariations(apiItem, name)
                )
            }
            .filter { it.price > MIN_VALID_PRICE && it.price < MAX_VALID_PRICE } // Filter garbage
            .toList()

        // 2. Group items by name to create variant-aware objects
        val groupedItems = allMapped
            .groupBy { it.name.lowercase() }
            .map { entry ->
                val sameName = entry.value.distinctBy { it.price }.sortedBy { it.price }
                val base = sameName.first()

                when {
                    // The server said what this item's variations are - trust that over
                    // anything inferred from the list.
                    base.variants.isNotEmpty() -> base
                    // Fallback for menus that carry no variations block: rows that share a
                    // name but differ in price are the same dish at two prices.
                    sameName.size > 1 -> base.copy(variants = sameName)
                    else -> base
                }
            }

        // 3. Extract unique categories
        val categories = allApiItems
            .asSequence()
            .filter { it.categoryName != null }
            .map { Category(it.categoryId.coerceToInt(), it.categoryName!!.trim()) }
            .distinctBy { it.id }
            .sortedBy { it.id }
            .toList()

        return ParsedMenu(categories = categories, items = groupedItems.sortedBy { it.name })
    }

    /** Bar items and anything marked non-veg get the red mark. */
    private fun isVegType(itemType: String?): Boolean {
        val type = itemType?.lowercase()
        return type?.contains("non") != true && type?.contains("bar") != true
    }

    /**
     * The sellable children of [apiItem], cheapest first.
     *
     * Bad rows are dropped with the same price sanity check the main list uses (this menu
     * carries 49500.00 placeholder rows), and children that land on the same price are
     * collapsed - the server attaches one child per service category, so the identical
     * price appears twice and would otherwise show as two indistinguishable options.
     */
    private fun parseVariations(apiItem: ApiMenuItem, parentName: String): List<MenuItem> {
        val variations = apiItem.variations
        if (variations.isNullOrEmpty()) return emptyList()

        val parentImage = absoluteImageUrl(apiItem.itemImg)
        return variations
            .asSequence()
            .map { v ->
                val label = v.variationLabel?.trim().orEmpty()
                MenuItem(
                    id = v.id.coerceToInt(),
                    // Keeps the parent's name so the cart, KOT and bill read the same as
                    // the menu; the label is what tells two options apart.
                    name = parentName,
                    price = v.itemPrice.coerceToDouble(),
                    categoryId = apiItem.categoryId.coerceToInt(),
                    isVeg = isVegType(v.itemType ?: apiItem.itemType),
                    // The server cascades the parent's flag onto its children and sends
                    // it on every variation row, so the parent is only a fallback for a
                    // payload old enough not to carry it.
                    isInclusiveTax = (v.isInclusiveTax ?: apiItem.isInclusiveTax).coerceToBoolean(),
                    // A variation with no photo of its own falls back to the parent's,
                    // which is what the server does for the web POS.
                    imageUrl = absoluteImageUrl(v.itemImg).ifBlank { parentImage },
                    // The server labels a child after itself when it has no distinct name
                    // of its own; repeating the parent's name on every row says nothing.
                    variantLabel = if (label.equals(parentName, ignoreCase = true)) "" else label
                )
            }
            .filter { it.id > 0 && it.price > MIN_VALID_PRICE && it.price < MAX_VALID_PRICE }
            .distinctBy { it.price }
            .sortedBy { it.price }
            .toList()
    }

    /**
     * The photo URL as the device can actually fetch it.
     *
     * The server normally answers with an absolute URL built from its own host, but a
     * deployment that leaves that setting blank sends a bare path instead - which would
     * resolve against nothing on the device.
     */
    private fun absoluteImageUrl(raw: String?): String {
        val url = raw?.trim().orEmpty()
        return when {
            url.isEmpty() -> ""
            url.startsWith("http://", true) || url.startsWith("https://", true) -> url
            else -> NetworkModule.SERVER_ROOT.trimEnd('/') + "/" + url.trimStart('/')
        }
    }

    private fun parseCategory(
        categoryObj: JsonObject,
        allApiItems: MutableList<ApiMenuItem>
    ) {
        val catIdElement = categoryObj.get("category_id")
        val catId = when {
            catIdElement == null || catIdElement.isJsonNull -> 0
            catIdElement.isJsonPrimitive && catIdElement.asJsonPrimitive.isNumber -> catIdElement.asInt
            else -> catIdElement.asString.toIntOrNull() ?: 0
        }
        val catName = categoryObj.get("category_name")?.asString ?: "General"

        val itemsElement = categoryObj.get("items")
        if (itemsElement != null && itemsElement.isJsonArray) {
            val categoryItems: List<ApiMenuItem> =
                NetworkModule.gson.fromJson(itemsElement, ITEM_LIST_TYPE)
            categoryItems.mapTo(allApiItems) {
                it.copy(
                    // NOTE: kept byte-for-byte from the original on purpose. `toString()`
                    // on a Gson-decoded number yields "0.0", so toIntOrNull() returns null
                    // and a zero categoryId is *not* replaced by the parent category id.
                    // See the review notes — changing this shifts which chip an item files under.
                    categoryId = if (it.categoryId == null || it.categoryId.toString().toIntOrNull() == 0) catId else it.categoryId,
                    categoryName = it.categoryName ?: catName
                )
            }
        }
    }
}
