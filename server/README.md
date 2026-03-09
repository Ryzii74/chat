# GameChat Server

Совместимый сервер для текущего Android-клиента (`ChatServerClient`).

## Запуск

```bash
cd server
npm install
npm start
```

По умолчанию сервер слушает `0.0.0.0:8080`.

Переменные окружения:

- `HOST` (по умолчанию `0.0.0.0`)
- `PORT` (по умолчанию `8080`)
- `MAX_MESSAGES` (по умолчанию `200`)
- `DEFAULT_ROOM` (по умолчанию `general`)
- `ADMIN_PIN` (по умолчанию `1234`)
- `ADMIN_TOKEN_TTL_MS` (по умолчанию `604800000`, 7 дней)
- `DATA_DIR` (по умолчанию `./data`)
- `DATA_FILE` (по умолчанию `./data/chat-state.json`)

## API

- `GET /messages`
  - Ответ: `{"room":"general","activeRoom":"general","messages":[{"room":"general","user":"...","message":"...","timestamp":"..."}]}`
  - Всегда возвращает сообщения только активной комнаты.

- `POST /messages`
  - Тело: `{"user":"Player","message":"Hello","imageUrl":"/media/<id>.jpg"}`
  - Успех: `200` + текст `Message sent`
  - Ошибка: `400` + JSON с `error`
  - Сообщение всегда сохраняется в активную комнату.

- `DELETE /messages/<id>`
  - Удаляет конкретное сообщение из активной комнаты.
  - Успех: `200` + `{"status":"ok","deletedId":"<id>"}`

- `POST /media`
  - Бинарное тело `image/jpeg`
  - Успех: `200` + `{"url":"/media/<id>.jpg"}`

- `GET /media/<id>.jpg`
  - Возвращает изображение.

- `POST /admin/login`
  - Тело: `{"pin":"1234"}`
  - Успех: `200` + `{"status":"ok","adminToken":"<token>"}`

- `POST /admin/logout`
  - Header: `X-Admin-Token: <token>`
  - Успех: `200` + `{"status":"ok"}`

- `POST /admin/switch-room`
  - Header: `X-Admin-Token: <token>`
  - Тело: `{"room":"new-room"}`
  - Успех: `200` + `{"status":"ok","activeRoom":"new-room"}`

- `POST /admin/clear-room`
  - Header: `X-Admin-Token: <token>`
  - Тело: `{"room":"new-room"}`
  - Успех: `200` + `{"status":"ok","clearedRoom":"new-room"}`

## Персистентность

Состояние сервера (активная комната, список разрешённых ников и история по всем комнатам) сохраняется в `chat-state.json` и восстанавливается при перезапуске.
- `WS /ws`
  - События от сервера:
  - `{"type":"connected","activeRoom":"general"}`
  - `{"type":"message","activeRoom":"general"}`
  - `{"type":"message_deleted","messageId":"...","activeRoom":"general"}`
  - `{"type":"room_switched","activeRoom":"new-room"}`
  - `{"type":"room_cleared","room":"new-room","activeRoom":"new-room"}`
