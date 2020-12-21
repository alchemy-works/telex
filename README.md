# Telex

Simple Telegram API wrapper

- [Telegram Bot API](https://core.telegram.org/bots/api)
- JDK 11+ required
- Modularity: `telegram.telex`

## Usage

```java
var telex = new Telex("{token}");
var result = telex.call("sendMessage", Map.of(
        "chat_id", 1234,
        "text", "hi👋"
));
```

## Dependencies

None!