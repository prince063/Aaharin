# Implementation Plan: POS Dashboard UI

Create a comprehensive POS (Point of Sale) dashboard UI for the Aaharin app, matching the provided screenshot. This layout will feature a split-screen design optimized for tablets/landscape mode, combining menu selection and cart management.

## Proposed Changes

### UI Components

#### [NEW] POSDashboard.kt (file:///C:/Users/princ/AndroidStudioProjects/Aaharin/app/src/main/java/com/example/aaharin/ui/menu/POSDashboard.kt)
- Implement a two-column layout using `Row`.
- **Left Column (Menu Selection):**
    - Search bar with "Add Item" button.
    - Horizontal category filter chips with item counts.
    - Scrollable grid of menu items grouped by categories (Recently Used, Favourite Items, etc.).
    - `POSMenuItemCard`: A custom card for menu items showing image, veg/non-veg status, name, and price.
- **Right Column (Current Order Sidebar):**
    - Header with "Current Order" and Table information.
    - Order notes text area.
    - Scrollable list of cart items.
    - Order summary section with Grand Total.
    - Action buttons at the bottom: "Compl.", "Split", "Save Bill", "PRINT KOT", "Print Bill", "KOT+Bill".

#### [MODIFY] MenuViewModel.kt (file:///C:/Users/princ/AndroidStudioProjects/Aaharin/app/src/main/java/com/example/aaharin/ui/menu/MenuViewModel.kt)
- Add state for "Recently Used" items.
- Ensure category item counts are calculated and available for the UI.

#### [MODIFY] MainActivity.kt (file:///C:/Users/princ/AndroidStudioProjects/Aaharin/app/src/main/java/com/example/aaharin/MainActivity.kt)
- Update the `menu` navigation destination to use `POSDashboard` instead of the current `MenuScreen`.

## Verification Plan

### Manual Verification
- Deploy the app to an Android emulator or device in landscape mode/tablet size.
- Verify that the layout splits correctly into two columns.
- Test searching for items and filtering by categories.
- Add items to the cart and verify they appear in the right sidebar.
- Verify the action buttons at the bottom of the cart sidebar.
- Ensure the UI matches the styling in the provided screenshot (colors, spacing, icons).
