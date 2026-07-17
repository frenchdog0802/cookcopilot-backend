-- Table structures inferred from com.cookplanner.entity classes

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    hashed_password VARCHAR(255),
    salt VARCHAR(255),
    role VARCHAR(255) NOT NULL DEFAULT 'user',
    connect_account VARCHAR(255),
    google_id VARCHAR(255),
    auth0_id VARCHAR(255),
    picture VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS ai_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    role VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    token_in INTEGER DEFAULT 0,
    token_out INTEGER DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE INDEX IF NOT EXISTS idx_ai_messages_user_created
    ON ai_messages (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS folders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(255),
    icon VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS global_config (
    id UUID PRIMARY KEY,
    app_name VARCHAR(255) NOT NULL,
    default_locale VARCHAR(255) NOT NULL DEFAULT 'en-US',
    default_time_zone VARCHAR(255) NOT NULL DEFAULT 'UTC',
    default_measurement_system VARCHAR(255) NOT NULL DEFAULT 'metric',
    maintenance_mode VARCHAR(255) DEFAULT 'off',
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS ingredients (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    default_unit VARCHAR(255),
    image_url VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    amount_cents INTEGER NOT NULL,
    currency VARCHAR(255) DEFAULT 'USD',
    status VARCHAR(255) NOT NULL,
    issued_at BIGINT,
    provider_invoice_id VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS recipes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    folder_id UUID,
    meal_name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    instructions TEXT,
    image_url VARCHAR(255),
    image_public_id VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS recipe_steps_list (
    recipe_id UUID NOT NULL,
    step VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS meal_plans (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    recipe_id UUID NOT NULL,
    meal_type VARCHAR(255),
    serving_date VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS pantry_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    unit VARCHAR(255),
    notes VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    amount_cents INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    paid_at BIGINT,
    provider_payment_id VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS subscription_plans (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    billing_period VARCHAR(255) NOT NULL,
    price_cents INTEGER NOT NULL,
    currency VARCHAR(255) DEFAULT 'USD',
    is_active BOOLEAN DEFAULT TRUE,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS plan_entitlements (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    key VARCHAR(255) NOT NULL,
    value VARCHAR(255) NOT NULL,
    notes VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    quantity DOUBLE PRECISION NOT NULL,
    unit VARCHAR(255),
    note VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT,
    CONSTRAINT uk_recipe_ingredient UNIQUE (recipe_id, ingredient_id)
);

CREATE TABLE IF NOT EXISTS shopping_list_items (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    ingredient_id UUID NOT NULL,
    quantity DOUBLE PRECISION,
    unit VARCHAR(255),
    checked BOOLEAN DEFAULT FALSE,
    has_been_added_to_pantry BOOLEAN DEFAULT FALSE,
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS steps (
    id UUID PRIMARY KEY,
    recipe_id UUID NOT NULL,
    step_no INTEGER NOT NULL,
    instruction TEXT NOT NULL,
    image_url VARCHAR(255),
    timer_second VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    status VARCHAR(255) NOT NULL,
    start_at BIGINT,
    current_period_start BIGINT,
    current_period_end BIGINT,
    cancel_at BIGINT,
    provider VARCHAR(255) DEFAULT 'stripe',
    provider_customer_id VARCHAR(255),
    provider_subscription_id VARCHAR(255),
    created_at BIGINT,
    updated_at BIGINT
);

CREATE TABLE IF NOT EXISTS usage_quotas (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    period_plan VARCHAR(255) DEFAULT 'monthly',
    period_start BIGINT,
    period_end BIGINT,
    recipes_created INTEGER DEFAULT 0,
    ai_message_sent INTEGER DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT
);

-- -----------------------------------------------------------------------------
-- Mock Data for Application Initialization
-- -----------------------------------------------------------------------------

-- User Mock Data
INSERT INTO users (id, first_name, last_name, name, email, hashed_password, role, created_at, updated_at) VALUES 
('e1f81c9a-4c22-4d29-a1fc-22d7a2cd9121', 'John', 'Doe', 'John Doe', 'john.doe@example.com', 'hashed_pw_mock', 'user', 1700000000, 1700000000);

-- Ingredients Mock Data
INSERT INTO ingredients (id, name, default_unit, created_at, updated_at) VALUES
('d18305f8-d450-4ff6-98a2-cbe8ddbd1910', 'Tomato', 'pcs', 1700000000, 1700000000),
('e98b7e8d-4a11-4f9e-bbb8-45be5a210515', 'Pasta', 'g', 1700000000, 1700000000),
('71d2b85e-bbd7-40af-b9ae-c692afc83c48', 'Garlic', 'clove', 1700000000, 1700000000),
('3a551de1-9d29-4f7f-a6f6-4bb2b291a1df', 'Chicken Breast', 'g', 1700000000, 1700000000),
('c4c2a1b1-6b2c-47b2-bcc0-18e3c54afbef', 'Olive Oil', 'ml', 1700000000, 1700000000);

-- Recipes Mock Data
INSERT INTO recipes (id, user_id, meal_name, description, instructions, created_at, updated_at) VALUES
('934d4d62-632b-426c-8bb1-e35b71db3a49', 'e1f81c9a-4c22-4d29-a1fc-22d7a2cd9121', 'Tomato Garlic Pasta', 'A simple and delicious pasta dish.', '1. Boil pasta. 2. Sauté garlic in olive oil. 3. Add chopped tomatoes. 4. Mix with pasta.', 1700000000, 1700000000),
('ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', 'e1f81c9a-4c22-4d29-a1fc-22d7a2cd9121', 'Grilled Garlic Chicken', 'Healthy grilled chicken breast marinated with garlic and olive oil.', '1. Marinate chicken with garlic and olive oil for 30 minutes. 2. Grill until fully cooked.', 1700000000, 1700000000);

-- Recipe Ingredients Mock Data
INSERT INTO recipe_ingredients (id, recipe_id, ingredient_id, quantity, unit, created_at, updated_at) VALUES
('90327f12-ea56-4279-8806-a51cc20e9808', '934d4d62-632b-426c-8bb1-e35b71db3a49', 'e98b7e8d-4a11-4f9e-bbb8-45be5a210515', 200, 'g', 1700000000, 1700000000),
('b301c3bc-7ab3-4d43-98fc-8f6fc6e205ab', '934d4d62-632b-426c-8bb1-e35b71db3a49', 'd18305f8-d450-4ff6-98a2-cbe8ddbd1910', 3, 'pcs', 1700000000, 1700000000),
('6fc56f26-7e23-44f2-9860-e8ea4749f7e4', '934d4d62-632b-426c-8bb1-e35b71db3a49', '71d2b85e-bbd7-40af-b9ae-c692afc83c48', 2, 'clove', 1700000000, 1700000000),
('46ef0cd8-3c35-430c-ab2f-8ae92e077180', '934d4d62-632b-426c-8bb1-e35b71db3a49', 'c4c2a1b1-6b2c-47b2-bcc0-18e3c54afbef', 15, 'ml', 1700000000, 1700000000),
('3f21a48c-7f51-41ee-a8a2-23c886ab8eeb', 'ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', '3a551de1-9d29-4f7f-a6f6-4bb2b291a1df', 300, 'g', 1700000000, 1700000000),
('1cae7960-9d0a-40a2-aa59-d81ff57388aa', 'ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', '71d2b85e-bbd7-40af-b9ae-c692afc83c48', 3, 'clove', 1700000000, 1700000000),
('f61e4b85-d87b-4d45-bab8-8e652fb6a22c', 'ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', 'c4c2a1b1-6b2c-47b2-bcc0-18e3c54afbef', 20, 'ml', 1700000000, 1700000000);

-- Steps Mock Data
INSERT INTO steps (id, recipe_id, step_no, instruction, created_at, updated_at) VALUES
('b39923ed-9cf3-4886-9a29-b69c4c5cf233', '934d4d62-632b-426c-8bb1-e35b71db3a49', 1, 'Boil a pot of salted water and cook the pasta according to package instructions.', 1700000000, 1700000000),
('c7deeb4b-226e-4bce-be4f-3e81881c9cf4', '934d4d62-632b-426c-8bb1-e35b71db3a49', 2, 'Heat olive oil in a pan, add minced garlic, and sauté until fragrant.', 1700000000, 1700000000),
('eacbff77-8ae3-4d69-b5fe-f13ad7a8f11d', '934d4d62-632b-426c-8bb1-e35b71db3a49', 3, 'Add chopped tomatoes to the pan and cook for 5 minutes. Mix with the drained pasta.', 1700000000, 1700000000),
('d2994cba-7ebf-410a-baeb-cd460d36ba00', 'ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', 1, 'Marinate chicken piece with minced garlic, olive oil, salt, and pepper.', 1700000000, 1700000000),
('421d09e5-6b58-48b8-b1c4-1be6455cb3b0', 'ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', 2, 'Preheat the grill and cook the chicken until internal temperature reaches 165F.', 1700000000, 1700000000);

-- Recipe Steps List (Legacy / simple steps implementation) Mock Data
INSERT INTO recipe_steps_list (recipe_id, step) VALUES
('934d4d62-632b-426c-8bb1-e35b71db3a49', 'Boil pasta'),
('934d4d62-632b-426c-8bb1-e35b71db3a49', 'Sauté garlic'),
('934d4d62-632b-426c-8bb1-e35b71db3a49', 'Add tomatoes and mix'),
('ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', 'Marinate chicken'),
('ffb51e06-d5fe-4e4b-9e4a-5c24d86b9eb4', 'Grill chicken');
