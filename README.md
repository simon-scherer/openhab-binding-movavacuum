# Mova/Dreame Vacuum Binding

Unofficial binding for Mova, Dreame and Trouver robot vacuums that are connected to the Mova/DreameHome cloud.
It was developed and tested with a **Mova V50 Ultra** (model `mova.vacuum.r2582a`). Other models of the same
protocol family should work for most channels; channels a device does not support simply stay empty.

The protocol knowledge (cloud API, properties, actions, map format, error codes) comes from the Home Assistant
integration [Tasshack/dreame-vacuum](https://github.com/Tasshack/dreame-vacuum) (MIT license), currently v2.0.3.

> **Note:** This binding uses the unofficial cloud API of the manufacturer. It may stop working at any time if
> the manufacturer changes the API. Use at your own risk.

German documentation: [README_de.md](README_de.md).
All texts the binding writes into channels (error texts, room lists, ...) follow the language set in the openHAB
regional settings (German or English).

## Supported Things

| Thing type | Description |
|------------|-------------|
| `account`  | Bridge: login to the Mova/DreameHome cloud account |
| `vacuum`   | A robot vacuum of that account |

## Discovery

There is no automatic discovery yet. After the account bridge is online, the binding writes one line per device
to the openHAB log:

```
Mova/Dreame device found: did=123456789 model=mova.vacuum.r2582a name=Robbi
```

Use the `did` to create the vacuum thing.

## Thing Configuration

### `account` Bridge

| Parameter | Description | Default |
|-----------|-------------|---------|
| `username` | E-mail address of the Mova/Dreamehome account | |
| `password` | Password of the account | |
| `accountType` | `mova` (Mova app), `dreame` (Dreamehome app) or `trouver` | `mova` |
| `country` | Cloud region: `eu`, `us`, `cn`, `sg`, `ru`, `kr`, `by` | `eu` |
| `pollingInterval` | Polling interval in seconds | 20 |

### `vacuum` Thing

| Parameter | Description | Default |
|-----------|-------------|---------|
| `did` | Device ID from the log (see Discovery) | |
| `settingsInterval` | Polling interval for the settings in seconds (min. 10) | 20 |
| `mapKey` | (advanced) 16 character IV for map decryption, only needed for unknown models | |
| `mapRotation` | (advanced) Map image rotation: -1 = automatic, 0, 90, 180, 270 | -1 |
| `mapFlip` | (advanced) Flip the map image vertically (on = looks like the app) | true |
| `showHiddenRooms` | (advanced) Draw rooms hidden in the app in grey | false |
| `mapSize` | (advanced) Edge length of the map image in pixels (200–1600) | 600 |
| `mapLabels` | (advanced) Room labels in the map image: `name`, `number`, `both` | name |

Existing things get new channels added automatically when the binding starts.

## Channels

### Status and control

| Channel | Type | Description |
|---------|------|-------------|
| state | String | sweeping, idle, paused, error, returning, charging, washing, drying, ... (read only) |
| status | Number | Raw status code (siid 4 / piid 1) |
| battery | Number:Dimensionless | Battery level |
| charging | Switch | Charging |
| error | Number | Error code, 0 = no error |
| error-text | String | Description of the error code |
| command | String | `start`, `pause`, `stop`, `dock`, `locate`. `start` also resumes a paused mop washing or return to the station, `start` and `dock` resume a paused return for auto empty (as in Tasshack 2.0.3). |
| suction | String | `quiet`, `standard`, `strong`, `turbo` |
| volume | Number:Dimensionless | Voice volume 0–100 % |
| dnd | Switch | Do not disturb active (read only) |
| cleaning-mode | String | `sweeping`, `mopping`, `sweeping_and_mopping`, `mopping_after_sweeping` |
| water-level | Number | Mop water level 1–32 |
| cleangenius | String | `off`, `routine_cleaning`, `deep_cleaning` |
| cleangenius-mode | String | `vacuum_and_mop`, `mop_after_vacuum` |
| charge-limit | Number | Charge limit 80–100 % |
| off-peak-charging | Switch | Off-peak charging on/off |
| off-peak-start, off-peak-end | String | Start and end of the off-peak period (`HH:mm`) |
| cleaning-mode-raw, water-raw | Number | Raw values for diagnostics |

### Statistics and history

| Channel | Type | Description |
|---------|------|-------------|
| cleaning-time | Number:Time | Duration of the current/last cleaning |
| cleaned-area | Number:Area | Area of the current/last cleaning |
| total-cleaning-time | Number:Time | Total cleaning time |
| total-cleaned-area | Number:Area | Total cleaned area |
| cleaning-count | Number | Number of cleanings |
| last-cleaning | String | Last cleaning: start, duration, area, result |
| cleaning-history | String | The last 10 cleanings, one line each |

### Cleaning commands

Coordinates are given in map units (mm). Use the channel `room-zones` to find suitable values.

| Channel | Type | Description |
|---------|------|-------------|
| clean-rooms | String | Rooms by number or name, separated by comma, with optional options after `@` (separated by `/`): `times=1-3`, `suction=quiet..turbo`, `water=1-3`, `room` (use the room settings stored in the app). Example: `Kitchen@times=2/suction=strong, 7@room` |
| clean-zone | String | `x1,y1,x2,y2[@times=../suction=../water=..]`, several zones separated by `;` |
| clean-spot | String | `x,y[@times=../suction=../water=..]`, about 1.5 m² around each point, several separated by `;` |
| go-to | String | `x,y` – drive to a point and stay there (models with camera) |
| remote-control | String | One step: `forward`, `backward`, `left`, `right`, `stop` or `rotation,velocity` (-360..360, -600..600) |
| shortcuts | String | List of the shortcuts defined in the app (read only) |
| shortcut | String | `start:<number or name>`, `rename:<number>=<new name>`, `delete:<number>` |

Note: `times` is applied by the robot. `suction` and `water` per room are sent, but the V50 Ultra seems to use
its stored room settings instead. Use `room-settings` to change them permanently.

### Rooms and map

| Channel | Type | Description |
|---------|------|-------------|
| rooms | String | Rooms as list `1: Living room, 2: Kitchen, ...` |
| room-info | String | One line per room: mode, suction, water, repeats, order, last cleaning |
| room-settings | String | Write room settings: `Kitchen@times=2/suction=strong/water=2/mode=sweeping_and_mopping/order=1; 9@times=1`. Only while docked. |
| room-edit | String | Edit rooms: `Room@name=New name/visible=on\|off/floor=none\|wood\|tile/mop=A\|B\|C`, several rooms separated by `;`. Not during cleaning. |
| room-zones | String | One line per room: bounding rectangle and a safely reachable point near the room centre |
| room-progress | String | Done, skipped and pending rooms of the running job |
| current-room | String | Room the robot is in |
| robot-position | String | Robot position (`x,y`), room, station position and map timestamp |
| obstacles | String | Detected obstacles with number, type and position |
| obstacle-ignore | String | `<number>=ignore` or `<number>=restore`. Not during cleaning. |
| map-image | Image | Rendered map (PNG, light theme, transparent background) |
| map-image-dark | Image | Rendered map in the dark theme (only rendered while an item is linked) |
| map-refresh | Switch | `ON` reloads the map immediately |
| map-command | String | `rename:<new name>` or `backup` (back up the current map in the cloud). Not during cleaning. |

In a Main UI page you can follow the openHAB theme automatically, e.g. in an `oh-image-card`:
`item: =themeOptions.dark === 'dark' ? 'Robbi_MapDark' : 'Robbi_Map'`.

The map is loaded at startup, every 30 minutes, every minute while cleaning and shortly after a cleaning has
finished.

### Consumables

| Channel | Type | Description |
|---------|------|-------------|
| main-brush-left, side-brush-left, filter-left, sensor-left, wheel-left, scale-inhibitor-left | Number:Dimensionless | Remaining lifetime in % |
| wheel-time-left, scale-inhibitor-time-left | Number:Time | Remaining time in hours |
| washboard-left, washboard-time-left | | Washboard cleaning; only created if the device reports these values |
| reset-consumable | String | `main_brush`, `side_brush`, `filter`, `sensor`, `wheel`, `scale_inhibitor`, `washboard` |

### Base station

| Channel | Type | Description |
|---------|------|-------------|
| base-command | String | `wash_mop`, `pause_wash`, `resume_wash`, `dry_mop`, `stop_dry_mop`, `dry_dust_bag`, `stop_dry_dust_bag`, `drain_dirty_water`, `empty_clean_tank`, `empty_dust` |
| clean-water-tank | String | installed, not_installed, low_water, checking |
| dirty-water-tank | String | installed, not_installed_or_full |
| dust-bag | String | installed, not_installed, check |
| detergent-status | String | installed, disabled, low_detergent |
| base-status | String | idle, washing, drying, returning, paused, clean_add_water, returning_to_drain, returning_for_dry_mop |
| auto-empty-status | String | idle, active, not_performed, returning |
| dust-collection | String | not_available, available, over_use, never |
| dust-bag-drying-status | String | idle, drying, paused |
| drainage-status | String | idle, draining, draining_success, draining_failed |
| drying-progress | Number:Dimensionless | Mop drying progress |
| mop-in-station | Switch | Mop is in the station |

### Settings

About 50 settings of the app are available as channels (read and write). Settings the device does not report
stay empty and are not written.

Switches: `carpet-boost`, `carpets-first`, `side-brush-carpet-rotate`, `intensive-carpet-cleaning`,
`collision-avoidance`, `auto-mount-mop`, `floor-direction-cleaning`, `dynamic-obstacle-cleaning`,
`obstacle-avoidance`, `intelligent-stain-cleaning`, `large-particles`, `obstacle-crossing`,
`clearance-leg-crossing`, `active-suspension-crossing`, `lift-chassis-on-carpet`, `close-roller-cover-on-carpet`,
`side-reach`, `mop-extend`, `gap-cleaning-extension`, `mopping-under-furniture`, `dnd-disable-auto-empty`,
`auto-drying`, `smart-drying`, `silent-drying`, `dust-bag-drying`, `self-clean`, `water-electrolysis`,
`mopping-with-detergent`, `smart-mop-washing`, `hot-washing`, `uv-sterilization`, `ultra-clean-mode`,
`resume-cleaning`, `pet-detection`, `obstacle-pictures`, `fill-light`, `mop-washing-with-detergent`, `child-lock`.

Options (String): `carpet-cleaning`, `auto-lds`, `low-lying-frequency`, `mop-extend-frequency`, `drying-time`,
`auto-empty-mode`, `mop-wash-level`, `scraper-frequency`, `water-temperature`, `self-clean-frequency`,
`mopping-type`. The allowed values are offered in the UI.

If smart obstacle avoidance is off, the app also disables stain cleaning and dynamic obstacle cleaning; the
binding then refuses to switch these on and logs a warning.

## Full Example

Ready-to-use example files are in [doc/examples](doc/examples): [things](doc/examples/movavacuum.things), [items for all channels](doc/examples/movavacuum.items), [rules](doc/examples/movavacuum.rules) (room selection, Alexa) and a [Main UI page](doc/examples/vacuum-page.yaml) with a map that follows the light/dark theme.

`movavacuum.things`:

```java
Bridge movavacuum:account:home "Mova Account" [ username="me@example.com", password="secret", accountType="mova", country="eu" ] {
    Thing vacuum robbi "Robbi" [ did="123456789" ]
}
```

`movavacuum.items`:

```java
String Robbi_State      "State [%s]"         { channel="movavacuum:vacuum:home:robbi:state" }
Number:Dimensionless Robbi_Battery "Battery [%d %%]" { channel="movavacuum:vacuum:home:robbi:battery" }
String Robbi_Command    "Command"            { channel="movavacuum:vacuum:home:robbi:command" }
String Robbi_CleanRooms "Clean rooms"        { channel="movavacuum:vacuum:home:robbi:clean-rooms" }
String Robbi_ErrorText  "Error [%s]"         { channel="movavacuum:vacuum:home:robbi:error-text" }
Image  Robbi_Map        "Map"                { channel="movavacuum:vacuum:home:robbi:map-image" }
```

Rule example (clean kitchen and hallway twice):

```java
Robbi_CleanRooms.sendCommand("Kitchen@times=2, Hallway@times=2")
```

## Troubleshooting

Enable debug logging: `log:set DEBUG org.openhab.binding.movavacuum`. The raw values of the first queries are
written to the log once.

- "Login rejected": check account type and region.
- Device list empty: try another region.
- A channel stays empty: the device probably does not support that property.
- `code 80001` in the debug log: the cloud could not reach the robot (asleep or offline). Harmless if it happens
  occasionally.

## Credits

- [Tasshack/dreame-vacuum](https://github.com/Tasshack/dreame-vacuum) (MIT license) for the protocol, the map
  format and the error texts.
