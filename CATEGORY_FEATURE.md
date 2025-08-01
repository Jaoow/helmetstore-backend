# Product Categories Feature

## Overview

The HelmetStore now supports product categories. When creating a product, you can specify a category name. If the category exists, it will be associated with the product; if it doesn't exist, a new category will be created automatically.

## Implementation Details

### Models

- **Category**: Entity with `id` and `name` (unique) fields
- **Product**: Updated to include a `@ManyToOne` relationship with Category

### Services

- **CategoryService**: Handles category operations including `findOrCreateCategory()` method
- **ProductService**: Updated to handle category assignment during product creation/update

### DTOs

- **CategoryDTO**: For transferring category data (name only)
- **ProductCreateDTO**: Updated to include `categoryName` field
- **ProductDto**: Updated to include `categoryName` field only

### Controllers

- **CategoryController**: Provides REST endpoints for category management
- **ProductController**: Existing controller works with updated DTOs

## API Endpoints

### Categories

- `GET /api/categories` - Get all categories
- `GET /api/categories/{id}` - Get category by ID
- `POST /api/categories` - Create new category (ADMIN only)
- `PUT /api/categories/{id}` - Update category (ADMIN only)
- `DELETE /api/categories/{id}` - Delete category (ADMIN only)

### Products (Updated)

- `POST /api/products` - Create product with category
- `PUT /api/products/{id}` - Update product with category

## Usage Examples

### Creating a Product with Category

```json
POST /api/products
{
  "model": "Sport Helmet",
  "color": "Red",
  "imgUrl": "https://example.com/helmet.jpg",
  "categoryName": "Motorcycle Helmets",
  "variants": [
    {
      "sku": "SPORT-RED-M",
      "size": "M",
      "quantity": 10
    }
  ]
}
```

### Response includes category information

```json
{
  "id": 1,
  "model": "Sport Helmet",
  "color": "Red",
  "imgUrl": "https://example.com/helmet.jpg",
  "categoryName": "Motorcycle Helmets",
  "variants": [...]
}
```

### Updating a Product with Category

```json
PUT /api/products/1
{
  "model": "Sport Helmet",
  "color": "Blue",
  "categoryName": "Motorcycle Helmets",
  "variants": [...]
}
```

## Database Schema

The following tables are automatically created by Hibernate:

- `categories` table with `id` and `name` columns
- `products` table updated with `category_id` foreign key

## Frontend Integration

When implementing the frontend:

1. Use the `categoryName` field when creating products
2. Use the `categoryName` field when updating products
3. Display category information in product listings
4. Optionally provide category management UI using the category endpoints
5. Consider implementing category filtering in product search

## Notes

- Categories are automatically created if they don't exist
- Category names are trimmed and case-sensitive
- Empty or null category names are ignored
- The category relationship is optional (products can exist without categories)
- Product cache is automatically invalidated when categories are updated
- CategoryDTO only shows the name (no ID in responses)
- ProductDto only shows categoryName (no category object)
