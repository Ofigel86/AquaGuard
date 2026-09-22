package dev.aquaguard.checks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.aquaguard.checks.CheckInfo.Category.COMBAT;
import static dev.aquaguard.checks.CheckInfo.Category.MOVEMENT;
import static dev.aquaguard.checks.CheckInfo.Category.PLAYER;
import static dev.aquaguard.checks.CheckInfo.Category.WORLD;

public final class CheckCatalog {
    private static final Map<String, CheckInfo> BY_ID = new LinkedHashMap<>();

    static {
        add("SpeedA", MOVEMENT, "Слишком большой горизонтальный сдвиг за тик", false);
        add("SpeedB", MOVEMENT, "Слишком высокая средняя скорость в окне", false);
        add("FlyA", MOVEMENT, "Зависание в воздухе", false);
        add("FlyB", MOVEMENT, "Набор высоты без прыжка, элитр и левитации", false);
        add("NoFallA", MOVEMENT, "Нет урона после высокого падения", false);
        add("JesusA", MOVEMENT, "Слишком быстро в воде", false);
        add("JesusB", MOVEMENT, "Ходьба по поверхности воды", false);
        add("StepA", MOVEMENT, "Шаг выше атрибута step_height", false);
        add("PhaseA", MOVEMENT, "Нахождение внутри твёрдого блока", false);
        add("NoSlowA", MOVEMENT, "Нет замедления при использовании предмета", false);
        add("HighJumpA", MOVEMENT, "Прыжок выше физики и Jump Boost", false);
        add("ElytraA", MOVEMENT, "Полёт элитрами без элитр или без фейерверка", false);
        add("FastClimbA", MOVEMENT, "Слишком быстрый подъём по лестнице", false);
        add("NoWebA", MOVEMENT, "Нет замедления в паутине", false);
        add("StrafeA", MOVEMENT, "Мгновенный разгон", true);
        add("StrafeB", MOVEMENT, "Мгновенная смена направления", true);
        add("StrafeC", MOVEMENT, "Слишком высокая скорость в воздухе", true);
        add("TimerA", MOVEMENT, "Слишком частые пакеты движения", true);
        add("BlinkA", MOVEMENT, "Рывок после паузы в пакетах", true);
        add("SpiderA", MOVEMENT, "Карабканье по стене", true);
        add("BoatFlyA", MOVEMENT, "Лодка летит вне воды", true);
        add("GroundSpoofA", MOVEMENT, "Клиент говорит, что игрок на земле, а под ним воздух", true);
        add("InventoryMoveA", MOVEMENT, "Движение с открытым контейнером", true);
        add("VelocityA", MOVEMENT, "Игнор отдачи", true);

        add("ReachA", COMBAT, "Удар дальше entity_interaction_range", false);
        add("ReachB", COMBAT, "Стабильно длинная средняя дистанция удара", true);
        add("WallHitA", COMBAT, "Удар сквозь стену", false);
        add("HitboxA", COMBAT, "Удар, когда прицел не попадает в хитбокс", false);
        add("AutoClickerA", COMBAT, "Ровный или слишком высокий CPS ударов", false);
        add("MultiAuraA", COMBAT, "Много целей за короткое окно", false);
        add("AttackCooldownA", COMBAT, "Удары быстрее кулдауна оружия", true);
        add("AttackIntervalB", COMBAT, "Слишком ровный интервал ударов", true);
        add("AimSnapA", COMBAT, "Рывок прицела в цель", true);
        add("TargetSwitchC", COMBAT, "Мгновенное переключение цели в узком конусе", true);
        add("CriticalsA", COMBAT, "Крит без реального падения", true);
        add("KeepSprintA", COMBAT, "Нет замедления после удара", true);

        add("FastPlaceA", WORLD, "Слишком частая установка блоков", false);
        add("FastBreakA", WORLD, "Слишком частая добыча твёрдых блоков", false);
        add("PlaceReachA", WORLD, "Установка дальше block_interaction_range", false);
        add("BreakReachA", WORLD, "Добыча дальше block_interaction_range", false);
        add("AirPlaceA", WORLD, "Установка блока в воздухе без опоры", false);
        add("NukerA", WORLD, "Массовая добыча или ломание не глядя", false);
        add("ImpossibleBreakA", WORLD, "Блок сломан быстрее инструмента", false);
        add("ScaffoldA", WORLD, "Паттерн моста под себя", true);
        add("ScaffoldB", WORLD, "Заблокированный взгляд при мосте", true);
        add("TowerA", WORLD, "Башня быстрее физики прыжка", true);

        add("FastBowA", PLAYER, "Полный натяг лука быстрее ванили", false);
        add("FastEatA", PLAYER, "Еда съедена быстрее ванили", false);
        add("RegenA", PLAYER, "Сытость лечит чаще ванили", false);
        add("InvalidPitchA", PLAYER, "Pitch вне -90..90", false);
        add("GhostHandA", PLAYER, "Взаимодействие сквозь стену", false);
        add("AutoTotemA", PLAYER, "Тотем в левую руку за доли секунды до срабатывания", true);
        add("AutoTotemB", PLAYER, "Серия мгновенных тотемов", false);
        add("AutoTotemC", PLAYER, "Низкое HP, свап и тотем одним движением", true);
        add("ChestStealerA", PLAYER, "Слишком быстрый разбор контейнера", true);
        add("CrystalAuraA", PLAYER, "Кристалл ставится и бьётся почти в один тик", true);
        add("IllegalStackA", PLAYER, "Предмет сложен больше ванильного максимума", false);
        add("IllegalEnchantA", PLAYER, "Зачарование выше ванильного максимума", true);
    }

    private CheckCatalog() {}

    private static void add(String id, CheckInfo.Category category, String description, boolean experimental) {
        BY_ID.put(id.toLowerCase(Locale.ROOT), new CheckInfo(id, category, description, experimental));
    }

    public static List<CheckInfo> all() {
        return List.copyOf(BY_ID.values());
    }

    public static CheckInfo get(String id) {
        if (id == null) return null;
        return BY_ID.get(id.toLowerCase(Locale.ROOT));
    }

    public static String resolve(String id) {
        CheckInfo info = get(id);
        return info == null ? id : info.id();
    }

    public static List<CheckInfo> byCategory(CheckInfo.Category category) {
        List<CheckInfo> list = new ArrayList<>();
        for (CheckInfo info : BY_ID.values()) {
            if (category == null || info.category() == category) list.add(info);
        }
        return list;
    }
}
