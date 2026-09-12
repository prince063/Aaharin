package com.example.aaharin.ui.menu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aaharin.data.Category
import com.example.aaharin.data.MenuItem
import com.example.aaharin.data.ApiMenuItem
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

    fun fetchMenu(accessToken: String, restaurantId: Int, tableId: Int) {
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val response = NetworkModule.apiService.getItemList(
                    headerToken = accessToken,
                    queryToken = accessToken,
                    fieldToken = accessToken,
                    restaurantId = restaurantId,
                    tableId = tableId
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

                        if (parsed == null) {
                            errorMessage = "No items found in the menu."
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
                val type = apiItem.itemType?.lowercase()
                MenuItem(
                    id = apiItem.id.coerceToInt(),
                    name = apiItem.itemName!!.trim(),
                    price = apiItem.itemPrice.coerceToDouble(),
                    categoryId = apiItem.categoryId.coerceToInt(),
                    isVeg = type?.contains("non") != true && type?.contains("bar") != true,
                    isFavourite = apiItem.isFavourite.coerceToBoolean()
                )
            }
            .filter { it.price > MIN_VALID_PRICE && it.price < MAX_VALID_PRICE } // Filter garbage
            .toList()

        // 2. Group items by name to create variant-aware objects
        val groupedItems = allMapped
            .groupBy { it.name.lowercase() }
            .map { entry ->
                val variants = entry.value.distinctBy { it.price }.sortedBy { it.price }
                val base = variants.first()

                // If more than 1 price variant exists, store them in the list
                base.copy(variants = if (variants.size > 1) variants else emptyList())
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
