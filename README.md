# 9VESC

An Android app for scooters that run the Ninebot G30 dashboard on a VESC with
[Sharkboy-j/vesc_g30_dash](https://github.com/Sharkboy-j/vesc_g30_dash). It lets you change the speed modes and
throttle settings, choose what the G30 dash shows, and tune VESC power settings from your phone. The settings
work like the ones in ScooterHacking Utility and VESC Tool.

## Setup

1. Upload [`lisp/g30_dash_9vesc.lisp`](lisp/g30_dash_9vesc.lisp) to the controller with VESC Tool (VESC Tool → LispBM →
   open the file → Upload, then Run). It's the 6.05 ADC script with an app link added. Wiring and button controls stay the same.
2. Install the APK from Releases (or build it: `./gradlew assembleDebug`), tap **Connect** and choose your controller.

Your phone needs a Bluetooth connection to the controller that isn't on the UART the dash uses, for example the
built-in Bluetooth on a Spintend Ubox or a BLE module on a second UART.

## Tabs

- **Throttle**: settings for Eco, Drive and Sport (top speed, motor power, max watts, field weakening and regen braking),
  plus start mode, kick-start speed, throttle curve, throttle ramp up and down, cruise control, brake-light flashing and auto power off.
- **Display**: what the big digits show while riding and when stopped (speed, battery %, temperatures, amps, power, cell
  voltage, trip, duty, voltage), what the small red digits show, km/h or mph, and the temperatures that turn on the warning icon.
- **Advanced**: VESC motor and battery current limits, ERPM, duty, voltage cutoffs, motor temperature limits, field-weakening start,
  wheel size, motor poles, battery cells and input calibration. Changes apply straight away. Tap **Save to controller** to keep them after a power cycle.

Settings on the Throttle and Display tabs are saved on the controller's EEPROM as soon as you change them. The script
won't accept changes while the scooter is moving faster than 3 km/h, because writing to flash stops the motor for a moment.

## Protocol

The app sends COMM_CUSTOM_APP_DATA packets (`'G' cmd id f32`) to the script, and the script replies with every value it
manages. The ids are the positions in the script's `vars` and `confs` lists. See `proc-data` in the script.

## License

GPL-3.0, the same license as the original script.
