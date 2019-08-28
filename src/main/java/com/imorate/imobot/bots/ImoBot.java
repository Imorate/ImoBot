package com.imorate.imobot.bots;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imorate.imobot.properties.BotProperties;
import com.vdurmont.emoji.EmojiParser;
import kong.unirest.Unirest;
import org.apache.shiro.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.session.TelegramLongPollingSessionBot;

import javax.annotation.PostConstruct;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class ImoBot extends TelegramLongPollingSessionBot {

    private static final Logger logger = LoggerFactory.getLogger(ImoBot.class);
    private static final String PARAMETER_STATUS = "status";
    private static final Integer STATUS_WAIT_COMMAND = 1;
    private static final Integer STATUS_WEATHER_INPUT = 2;
    private final BotProperties botProperties;

    public ImoBot(BotProperties botProperties) {
        this.botProperties = botProperties;
    }

    @PostConstruct
    public void registerBot() {
        logger.info("Telegram Bot API initialized");
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(this);
            logger.info("{} bot is registered", this.getClass());
        } catch (TelegramApiException e) {
            logger.error("An error occurred: " + e.toString());
        }
    }

    @Override
    public void onUpdateReceived(Update update, Optional<Session> optionalSession) {
        if (update.hasMessage() && (update.getMessage().hasText() || update.getMessage().hasLocation())) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            Integer messageId = update.getMessage().getMessageId();
            Chat chat = update.getMessage().getChat();
            Session session = null;
            Integer status = null;
            if (optionalSession.isPresent()) {
                session = optionalSession.get();
                if (session.getAttribute(PARAMETER_STATUS) != null) {
                    status = (Integer) session.getAttribute(PARAMETER_STATUS);
                } else {
                    status = STATUS_WAIT_COMMAND;
                }
            }
            if (STATUS_WAIT_COMMAND.equals(status)) {
                if (messageText.equals("/start")) {
                    startCommand(chatId, chat);
                } else if (messageText.equals("/contact") || messageText.equals(EmojiParser.parseToUnicode(":telephone_receiver: Contact us"))) {
                    contactCommand(chatId, messageId);
                } else if (messageText.equals("/weather") || messageText.equals(EmojiParser.parseToUnicode(":white_sun_behind_cloud_rain: Show weather"))) {
                    weatherCommand(session, chatId, messageId);
                } else {
                    unknownCommand(chatId, messageId);
                }
            } else if (STATUS_WEATHER_INPUT.equals(status)) {
                if (update.getMessage().hasText() && messageText.equals(EmojiParser.parseToUnicode(":x: Cancel"))) {
                    cancelCommand(session, chatId);
                } else {
                    weatherProcess(session, chatId, update.getMessage());
                }
            }
        }
    }

    private void sendMessage(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            logger.error("Error in sending message: " + e.toString());
        }
    }

    private void startCommand(Long chatId, Chat chat) {
        SendMessage message = new SendMessage(chatId, EmojiParser.parseToUnicode("Welcome " + chat.getFirstName() + " " + chat.getLastName() + "!\n\n"
                + ":white_check_mark: You can use /weather command in order to show your weather information and /contact for getting in touch with us."));
        message.setReplyMarkup(mainMarkupKeyboard());
        sendMessage(message);
    }

    private void cancelCommand(Session session, Long chatId) {
        SendMessage message = new SendMessage(chatId, EmojiParser.parseToUnicode(":x: Current process is canceled"));
        message.setReplyMarkup(mainMarkupKeyboard());
        sendMessage(message);
        session.setAttribute(PARAMETER_STATUS, STATUS_WAIT_COMMAND);
    }

    private void weatherCommand(Session session, Long chatId, Integer messageId) {
        SendMessage message = new SendMessage(chatId, EmojiParser.parseToUnicode(":pushpin: Send your location to determine your weather information"))
                .setReplyToMessageId(messageId);
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup()
                .setResizeKeyboard(true).setOneTimeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.add(EmojiParser.parseToUnicode(":x: Cancel"));
        keyboardRows.add(keyboardRow);
        replyKeyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(replyKeyboardMarkup);
        sendMessage(message);
        session.setAttribute(PARAMETER_STATUS, STATUS_WEATHER_INPUT);
    }

    private void contactCommand(Long chatId, Integer messageId) {
        sendMessage(new SendMessage(chatId, EmojiParser.parseToUnicode(":link: Here is my Linktree profile\n\nhttps://linktr.ee/imorate"))
                .setReplyToMessageId(messageId));
    }

    private void unknownCommand(Long chatId, Integer messageId) {
        sendMessage(new SendMessage(chatId, EmojiParser.parseToUnicode(":x: Unknown command"))
                .setReplyToMessageId(messageId));
    }

    private void weatherProcess(Session session, Long chatId, Message message) {
        if (message.hasLocation()) {
            String response = Unirest.get("https://api.openweathermap.org/data/2.5/weather")
                    .queryString("appid", "e85b267179a51a7f1cde8f40670b5b56")
                    .queryString("lat", message.getLocation().getLatitude())
                    .queryString("lon", message.getLocation().getLongitude())
                    .queryString("units", "metric")
                    .asString()
                    .getBody();
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                JsonNode jsonNode = objectMapper.readTree(response);
                SendMessage msg = new SendMessage().setChatId(chatId).enableMarkdown(true);
                long datetime = jsonNode.path("dt").asLong();
                Date date = new Date(datetime * 1000L);
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
                String weatherDescription = "";
                for (JsonNode node : jsonNode.path("weather")) {
                    weatherDescription = node.path("description").asText();
                }
                msg.setText(EmojiParser.parseToUnicode(
                        ":pushpin: City: `" + jsonNode.path("name").asText() + "`\n" +
                                ":clock12: Time: `" + simpleDateFormat.format(date) + "`\n" +
                                ":compass: Coordinate: `" + message.getLocation().getLatitude() + ", " + message.getLocation().getLongitude() + "`\n\n" +
                                ":white_sun_behind_cloud_rain: Weather description: `" + weatherDescription + "`\n" +
                                ":arrow_down: Pressure: `" + jsonNode.path("main").path("pressure") + " hPa`\n" +
                                ":droplet: Humidity: `" + jsonNode.path("main").path("humidity") + "%`\n\n" +
                                ":temperature: Temperature: `" + jsonNode.path("main").path("temp") + " °C`\n" +
                                ":arrow_double_down: Maximum temperature: `" + jsonNode.path("main").path("temp_max") + " °C`\n" +
                                ":arrow_down: Minimum temperature: `" + jsonNode.path("main").path("temp_min") + " °C`\n\n" +
                                ":ocean: Sea level: `" + jsonNode.path("main").path("sea_level") + " hPa`\n" +
                                ":leaves: Ground level: `" + jsonNode.path("main").path("grnd_level") + " hPa`\n\n" +
                                ":dash: Wind speed: `" + jsonNode.path("wind").path("speed") + " meter/sec`\n" +
                                ":blowing_wind: Wind degree: `" + jsonNode.path("wind").path("deg") + "°`\n\n" +
                                ":cloud: Clouds: `" + jsonNode.path("clouds").path("all") + "%`\n"
                ));
                logger.info("Weather fetched: " + response);
                msg.setReplyMarkup(mainMarkupKeyboard());
                sendMessage(msg);
            } catch (Exception e) {
                logger.error("There is an error in fetching weather information: " + e.toString());
            }
            session.setAttribute(PARAMETER_STATUS, STATUS_WAIT_COMMAND);
            Unirest.shutDown();
        } else {
            sendMessage(new SendMessage(chatId, EmojiParser.parseToUnicode(":x: Invalid location. Send proper location."))
                    .setReplyToMessageId(message.getMessageId()));
        }
    }

    private ReplyKeyboardMarkup mainMarkupKeyboard() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup()
                .setResizeKeyboard(true);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow keyboardRow = new KeyboardRow();
        keyboardRow.add(EmojiParser.parseToUnicode(":white_sun_behind_cloud_rain: Show weather"));
        keyboardRow.add(EmojiParser.parseToUnicode(":telephone_receiver: Contact us"));
        keyboardRows.add(keyboardRow);
        replyKeyboardMarkup.setKeyboard(keyboardRows);
        return replyKeyboardMarkup;
    }

    @Override
    public String getBotUsername() {
        return botProperties.getUsername();
    }

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }
}