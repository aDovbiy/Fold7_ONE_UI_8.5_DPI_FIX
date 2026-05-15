# Fold 7 Density

Минимальное Android-приложение для Samsung Fold 7 без root. Команды выполняются через Shizuku от имени `shell`:

```sh
wm density 550 -d 0
wm density 455 -d 1
```

Официальный репозиторий Shizuku: https://github.com/RikkaW/AndroidShizuku

## Как пользоваться

1. Установи Shizuku на телефон.
2. Запусти Shizuku через Wireless debugging или через компьютер с ADB.
3. Собери и установи это приложение из Android Studio.
4. В приложении нажми `Дать доступ Shizuku`, затем `Подключить сервис`, затем `Применить 550 / 455`.

После перезагрузки телефона Shizuku в non-root режиме обычно нужно запускать заново.
