# Подключение Redmine MCP Server к Claude Code

Пошаговая инструкция по настройке AI-ассистента [Claude Code](https://docs.anthropic.com/en/docs/claude-code) с доступом к вашему Redmine через MCP-сервер.

## Содержание

1. [Настройка VPN](#1-настройка-vpn)
2. [Подписка Claude и установка Claude Code](#2-подписка-claude-и-установка-claude-code)
3. [Сборка MCP-сервера](#3-сборка-mcp-сервера)
4. [Подключение MCP-сервера к Claude Code](#4-подключение-mcp-сервера-к-claude-code)
5. [Проверка работы](#5-проверка-работы)

---

## 1. Настройка VPN

Для работы с Claude Code нужен доступ к сервисам Anthropic, а для доступа к Redmine — корпоративный VPN. Чтобы оба работали одновременно, нужен VPN с поддержкой **tunnel splitting** (раздельного туннелирования) — он направляет через VPN-туннель только нужный трафик, а остальной пускает напрямую.

Рекомендуемый вариант — [Amnezia VPN](https://amnezia.org):

1. Скачайте клиент с https://amnezia.org/downloads
2. Подключитесь к серверу (свой или арендованный VPS)
3. В настройках подключения включите **Split Tunneling** — добавьте в туннель только сайты/IP Anthropic, остальной трафик (включая корпоративный VPN) пойдёт напрямую

> Без tunnel splitting корпоративный VPN и VPN для Claude будут конфликтовать — работать сможет только один из них.

---

## 2. Подписка Claude и установка Claude Code

### Подписка vs API-ключ

Claude Code может работать двумя способами:

| | Подписка (Claude Pro / Max) | API-ключ |
|---|---|---|
| Стоимость | $20/мес (Pro) или $100/мес (Max) | Оплата за каждый запрос (~$15/1M токенов для Opus) |
| На практике | Фиксированная цена, безлимит в рамках тарифа | Активная работа легко выходит на $50–200+/мес |
| Удобство | Вход через браузер, не нужно управлять ключами | Нужно следить за балансом и лимитами |

**Подписка значительно выгоднее** — при активном использовании Claude Code с API-ключом расходы быстро превышают стоимость подписки Max. Рекомендуем начать с Pro ($20/мес).

### Регистрация

1. Откройте https://claude.ai
2. Зарегистрируйтесь (email или Google-аккаунт)
3. Оформите подписку Claude Pro или Max

### Установка Claude Code

#### Требования

- **Node.js 18** или новее — скачайте с https://nodejs.org

Проверьте установку Node.js:

```bash
node --version
```

#### Установка

Запустите cmd или PowerShell от администратора:

```bash
npm install -g @anthropic-ai/claude-code
```

#### Проверка и авторизация

Откройте **новое** окно терминала и запустите:

```bash
claude
```

При первом запуске Claude Code предложит авторизоваться — откроется браузер для входа в аккаунт Claude. После авторизации терминал готов к работе.

---

## 3. Сборка MCP-сервера

### Установка Java 25

MCP-сервер требует **Java 25** или новее. Скачайте JDK с https://www.oracle.com/java/technologies/downloads/ или любого другого поставщика OpenJDK. При установке убедитесь, что переменная среды `JAVA_HOME` указывает на каталог JDK, а `%JAVA_HOME%\bin` добавлен в `Path`.

Проверьте:

```bash
java -version
```

### Git

Убедитесь, что Git установлен и доступен в терминале. Если нет — скачайте с https://git-scm.com/downloads/win.

```bash
git --version
```

### Клонирование репозитория

```bash
git clone https://github.com/your-org/redmine-mcp-server.git
cd redmine-mcp-server
```

### Сборка

```bash
gradlew.bat bootJar
```

После успешной сборки JAR-файл появится по пути:

```
build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar
```

### Получение API-ключа Redmine

1. Войдите в ваш Redmine
2. Нажмите **Моя учётная запись** (вверху справа)
3. На правой панели найдите раздел **Ключ доступа к API**
4. Нажмите **Показать** и скопируйте ключ

---

## 4. Подключение MCP-сервера к Claude Code

Выполните в терминале:

```bash
claude mcp add --scope user \
  -e REDMINE_URL=http://адрес-вашего-redmine \
  -e REDMINE_API_KEY=ваш-redmine-api-ключ \
  -- redmine java -jar "C:/полный/путь/к/redmine-mcp-server/build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar"
```

> Флаг `--scope user` добавляет сервер глобально (для всех проектов). Без него сервер добавится только для текущего проекта.

> Если переменные `REDMINE_URL` и `REDMINE_API_KEY` уже заданы как переменные среды — флаги `-e` можно не указывать.

---

## 5. Проверка работы

1. Запустите Claude Code в папке с вашим проектом:

```bash
cd /путь/к/вашему/проекту
claude
```

2. Убедитесь, что MCP-сервер подключился. Выполните команду:

```
/mcp
```

В списке должен отображаться сервер `redmine` со статусом `connected`.

3. Попробуйте задать вопросы, связанные с Redmine:

```
Покажи мои задачи в Redmine
```

```
По каким рабочим дням в марте я внёс трудозатраты меньше 8 часов?
```

Claude Code вызовет инструменты MCP-сервера (`getMyIssues`, `getMyTimeEntries` и др.) и покажет результат.

### Доступные инструменты

MCP-сервер предоставляет инструменты только для **чтения** — он не может изменять данные в Redmine.

| Группа | Инструменты |
|--------|-------------|
| Пользователь | `getCurrentUser` |
| Проекты | `listProjects`, `getProject`, `listProjectMembers`, `listVersions` |
| Задачи | `listIssues`, `searchIssues`, `getIssue`, `getMyIssues` |
| Поиск | `searchAll` |
| Вложения | `listAttachments`, `getAttachmentContent`, `getImageAttachment` |
| Wiki | `listWikiPages`, `getWikiPage` |
| Трудозатраты | `listTimeEntries`, `getMyTimeEntries` |
| Справочники | `listQueries`, `listStatuses`, `listTrackers`, `listPriorities`, `listIssueCategories`, `listTimeEntryActivities` |

---

## Устранение проблем

**`java` не найден в терминале после установки**
Закройте и откройте терминал заново. Проверьте `JAVA_HOME` и `Path`.

**MCP-сервер не подключается**
Проверьте, что путь к JAR-файлу указан правильно и файл существует. Попробуйте запустить вручную:

```bash
java -jar /путь/к/redmine-mcp-server-0.1.0-SNAPSHOT.jar
```

В логе должно появиться: `Started RedmineMcpServerApplication` и `Registered tools: 23`.

**Нет доступа к Redmine**
Проверьте, что `REDMINE_URL` и `REDMINE_API_KEY` верны:

```bash
curl -H "X-Redmine-API-Key: ваш-ключ" http://адрес-redmine/users/current.json
```

Должен вернуться JSON с данными вашего пользователя.

**Claude Code не видит MCP-сервер после настройки**
Claude Code загружает MCP-серверы при запуске. После изменения конфигурации нужно полностью перезапустить Claude Code — не просто начать новый диалог, а закрыть и открыть заново. Проверить статус серверов: `claude mcp list`.

**MCP-сервер добавлен, но не отображается в `/mcp`**
Если вы добавляли сервер вручную, возможно, конфиг был записан не в тот файл. Удалите ручную конфигурацию и добавьте через CLI: `claude mcp add --scope user ...` — команда сама запишет в нужное место.
