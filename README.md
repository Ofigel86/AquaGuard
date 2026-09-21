# AquaGuard

Античит для Paper **1.21.4+**. Смотрит движение, бой, блоки и действия игрока через события сервера, без ProtocolLib. Наказания по умолчанию не банят: сначала алерт, setback и режим `simulate`.

## Что изменилось в 1.0.0

Старый плагин уже ловил скорость, флай, reach и тотемы, но часть чеков жила только в конфиге (`PlaceReach`, `BreakReach`, `AutoClicker`), а `StrafeB` мог флагать обычный шаг назад. Reload не обновлял тумблеры, `pvp-only: false` выключал урез урона целиком, владелец без OP не проходил permission команды, GUI не отменял drag, а распад VL мог не записываться обратно в карту.

Сейчас:

- 58 чеков в одном каталоге для GUI, команд и tab-complete
- экспериментальные проверки выключены, пока их явно не включат
- алерты с наведением и кликом-телепортом, звук, кулдаун, консоль, файл `logs/`
- Discord webhook без лишних библиотек
- лестница наказаний `log` / `execute` и старый per-check kick
- freeze с запретом взаимодействия и сохранением между рестартами
- bypass-коды в `bypass.yml`, генерация `/ag bypass code`
- GUI: игроки, чеки по категориям, bypass, история, setback, penalties
- `/ag watch`, `/ag info`, `/ag top`, `/ag history`, `/ag stats`
- формулы reach, скорости, CPS и времени добычи покрыты тестами

## Установка

1. Сервер Paper 1.21.4 или новее.
2. Положить `AquaGuard-1.0.0.jar` в `plugins`.
3. Перезапустить. Пороги — `plugins/AquaGuard/config.yml`.
4. В игре: `/ag gui` или `/ag ping`.

Автопривязка владельца выключена. Чтобы выдать доступ без OP, впиши UUID в `owner.uuids` и перезапусти, либо один раз включи `owner.auto-claim` со своим ником на online-mode сервере.

## Команды

| Команда | Что делает |
| --- | --- |
| `/ag ping` | версия, TPS, режим наказаний |
| `/ag gui [ник]` | меню, или карточка игрока |
| `/ag vl [ник]` | нарушения |
| `/ag vlreset <ник> [чек]` | сброс VL |
| `/ag alerts [on\|off]` | свои алерты |
| `/ag verbose` | подробности в чат |
| `/ag watch <ник>` | action bar со скоростью и VL |
| `/ag info <ник>` | пинг, бренд, мир, VL |
| `/ag checks list` | все чеки |
| `/ag checks <имя> <on\|off\|toggle>` | тумблер, пишется в `toggles.yml` |
| `/ag checks stable` | только стабильные |
| `/ag freeze <ник>` | заморозка |
| `/ag penalties <off\|simulate\|soft\|hard>` | урез урона |
| `/ag setback <on\|off\|toggle>` | откат позиции |
| `/ag bypass add\|remove\|list\|code` | обход |
| `/ag code <секрет>` | активировать код, доступно игроку |
| `/ag history [ник]` | флаги с рестарта |
| `/ag top` | онлайн по VL |
| `/ag flag <ник> <чек>` | тестовый флаг |
| `/ag webhook` | проверка Discord |
| `/ag settings clear` | убрать overrides из `settings.yml` |
| `/ag reload` | перечитать конфиг |

Право `ag.admin` (OP) открывает управление. `ag.alerts` — алерты. `ag.bypass` — не проверять игрока. `/ag code` намеренно без админки.

## Чеки

Стабильные включены, если `checks-toggles.enabled-by-default: true`.

**Движение:** SpeedA/B, FlyA/B, NoFallA, JesusA/B, StepA, PhaseA, NoSlowA, HighJumpA, ElytraA, FastClimbA, NoWebA.  
Эксперимент: StrafeA/B/C, TimerA, BlinkA, SpiderA, BoatFlyA, GroundSpoofA, InventoryMoveA, VelocityA.

**Бой:** ReachA (по атрибуту `entity_interaction_range`), WallHitA, HitboxA, AutoClickerA, MultiAuraA.  
Эксперимент: ReachB, AttackCooldownA, AttackIntervalB, AimSnapA, TargetSwitchC, CriticalsA, KeepSprintA.

**Мир:** FastPlaceA, FastBreakA, PlaceReachA, BreakReachA, AirPlaceA, NukerA, ImpossibleBreakA.  
Эксперимент: ScaffoldA/B, TowerA.

**Игрок:** FastBowA, FastEatA, RegenA, InvalidPitchA, GhostHandA, AutoTotemB, IllegalStackA.  
Эксперимент: AutoTotemA/C, ChestStealerA, CrystalAuraA, IllegalEnchantA.

Reach и place/break считают дистанцию до хитбокса, а не до центра. Скорость масштабируется атрибутом `movement_speed`, поэтому Speed II не нужно прописывать отдельно. Лёд, soul speed и depth strider учтены отдельно. Высокий пинг и низкий TPS отключают чувствительные чеки, но не phase и air-place.

## Наказания

`penalties.mode: simulate` только пишет, каким был бы урон. `soft` и `hard` режут урон в PvP.

`punishments.enabled: false`. Если включить, `mode: log` пишет команду в консоль, `mode: execute` выполняет её от консоли. Плейсхолдеры: `%player%`, `%check%`, `%vl%`, `%ping%`, `%world%`. Шаг лестницы срабатывает один раз.

Setback не трогает игроков с пингом выше `setback.max-ping-ms`.

## Сборка

```bash
mvn -B verify
```

Нужны Java 21 и доступ к `repo.papermc.io`. Тесты не поднимают сервер: они проверяют формулы VL, reach, CPS, scaffold и времени добычи.

## Ограничения

Это не пакетный античит. Таймер, блинк и ground spoof по событиям Bukkit грубее, чем разбор пакетов, поэтому они экспериментальные. Folia не поддерживается. Geyser получает дополнительный запас, если клиент представляется как Geyser или Floodgate.
