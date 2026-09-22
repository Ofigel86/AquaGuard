package dev.aquaguard.checks;

public record CheckInfo(String id, Category category, String description, boolean experimental) {
    public enum Category {
        MOVEMENT("Движение"),
        COMBAT("Бой"),
        WORLD("Мир"),
        PLAYER("Игрок");

        private final String title;

        Category(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        public static Category byName(String raw) {
            if (raw == null) return null;
            for (Category category : values()) {
                if (category.name().equalsIgnoreCase(raw) || category.title.equalsIgnoreCase(raw)) return category;
            }
            return null;
        }
    }
}
