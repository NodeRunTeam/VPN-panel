# Xui Admin

Android-приложение для администрирования панели **3X-UI** (проверялось по API v2.8.x).
Kotlin + Jetpack Compose, интерфейс в стиле iOS «жидкое стекло».

## Возможности
- Вход в панель (логин/пароль, данные хранятся в Android Keystore)
- Статус сервера: CPU, RAM, диск, сеть, аптайм, состояние и версия Xray, перезапуск Xray
- Список inbound'ов, включение/выключение
- Клиенты inbound'а: трафик, срок, онлайн-статус, включение/выключение, сброс трафика
- Автообновление каждые 10 секунд

## Сборка в GitHub
1. Создайте репозиторий и залейте проект (`git init && git add . && git commit -m init && git push`).
2. Откройте вкладку **Actions** — сборка стартует сама. APK будет в **Artifacts** (`XuiAdmin-apk`).
3. Релиз: `git tag v0.1.0 && git push --tags` — APK появится в GitHub Releases.

### Подписанный release (необязательно)
Создайте keystore: `keytool -genkeypair -v -keystore release.jks -alias xui -keyalg RSA -keysize 2048 -validity 10000`
Затем в Settings → Secrets and variables → Actions добавьте:
`KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Без секретов собирается debug-APK — он тоже устанавливается.

## Адрес панели
Указывайте полный адрес вместе с `webBasePath`, например `https://example.com:2053/mypanel`.
