package dev.akumavote;

import java.io.IOException;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

public final class VoteService {
    private final VoteStore store;
    private final Consumer<String> notifier;
    private final BooleanSupplier connected;
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "akumavote-http");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient http;

    public VoteService(VoteStore store, Consumer<String> notifier, BooleanSupplier connected) {
        this.store = store;
        this.notifier = notifier;
        this.connected = connected;
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        http = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<String> logs() {
        return List.copyOf(logs);
    }

    public boolean isRunning() {
        return running.get();
    }

    public void log(String message) {
        logs.add(message);
        if (logs.size() > 100) {
            logs.remove(0);
        }
        notifier.accept(message);
    }

    public boolean start() {
        if (!connected.getAsBoolean()) {
            return false;
        }
        if (!store.nickname().matches("[A-Za-z0-9_]{3,16}")) {
            log("Сначала сохраните корректный ник Minecraft (3–16 символов).");
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            log("Голосование уже выполняется.");
            return false;
        }
        cancelled.set(false);
        executor.execute(this::run);
        return true;
    }

    public boolean voteManually(int index) {
        if (!connected.getAsBoolean()) {
            log("Подключитесь к play.akumamc.net, чтобы голосовать.");
            return false;
        }
        if (index < 0 || index >= VoteSite.ALL.size()) {
            return false;
        }
        if (!store.nickname().matches("[A-Za-z0-9_]{3,16}")) {
            log("Сначала сохраните корректный ник Minecraft (3–16 символов).");
            return false;
        }
        if (store.isVoted(index)) {
            log("Голос уже учтён сегодня: " + VoteSite.ALL.get(index).name());
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            log("Дождитесь завершения текущего голосования.");
            return false;
        }
        cancelled.set(false);
        executor.execute(() -> {
            VoteSite site = VoteSite.ALL.get(index);
            log("Отправка голоса из игры: " + site.name());
            try {
                vote(site, index);
            } catch (IOException | RuntimeException e) {
                log("Ошибка внутриигрового голосования " + site.url() + ": " + e);
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    public void cancel() {
        if (running.get()) {
            cancelled.set(true);
            log("Остановка процесса голосования запрошена.");
        }
    }

    private void run() {
        log("Начало автоматического голосования (12 сайтов).");
        try {
            for (int index = 0; index < VoteSite.ALL.size(); index++) {
                if (cancelled.get() || !connected.getAsBoolean()) {
                    break;
                }
                if (store.isVoted(index)) {
                    log("Уже отмечен сегодня: " + VoteSite.ALL.get(index).name());
                    continue;
                }
                if (index > 0) {
                    try {
                        Thread.sleep(store.delay() * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (cancelled.get() || !connected.getAsBoolean()) {
                        break;
                    }
                }
                try {
                    vote(VoteSite.ALL.get(index), index);
                } catch (IOException | RuntimeException e) {
                    log("Ошибка запроса " + VoteSite.ALL.get(index).url() + ": " + e);
                }
            }
        } finally {
            running.set(false);
            log(cancelled.get() || !connected.getAsBoolean() ? "Процесс голосования остановлен." : "Процесс голосования завершён.");
        }
    }

    private void vote(VoteSite site, int index) throws IOException {
        if (cancelled.get() || !connected.getAsBoolean()) {
            return;
        }
        if (site.url().contains("#vote")) {
            log("Сайт использует JavaScript; отправка голоса из игры недоступна: " + site.name());
            return;
        }
        HttpRequest pageRequest = HttpRequest.newBuilder(URI.create(site.url()))
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        String html;
        try {
            HttpResponse<String> response = request(pageRequest);
            html = response.body();
            logResponse(response, html);
            if (!isSuccessful(response)) {
                return;
            }
        } catch (IOException e) {
            throw e;
        }
        if (hasChallenge(html)) {
            log("Обнаружена CAPTCHA / Cloudflare: " + site.name() + ". Внутриигровая отправка недоступна.");
            return;
        }
        for (HtmlForm form : parseForms(html)) {
            if (!form.method.equalsIgnoreCase("post")) {
                continue;
            }
            FormInput nicknameInput = null;
            for (FormInput input : form.inputs) {
                String name = input.name.toLowerCase(Locale.ROOT);
                String type = input.type.toLowerCase(Locale.ROOT);
                if ((type.isEmpty() || type.equals("text")) && (name.contains("nick") || name.contains("user")
                        || name.contains("player") || name.contains("minecraft") || name.equals("ign"))) {
                    nicknameInput = input;
                    break;
                }
            }
            if (nicknameInput == null) {
                continue;
            }
            URI action = URI.create(site.url()).resolve(form.action);
            if (!"https".equalsIgnoreCase(action.getScheme())
                    || !URI.create(site.url()).getHost().equalsIgnoreCase(action.getHost())) {
                log("Небезопасный адрес формы, отправка отменена: " + action);
                return;
            }
            List<FormInput> fields = new ArrayList<>();
            for (FormInput input : form.inputs) {
                if (input == nicknameInput) {
                    fields.add(new FormInput(input.name, input.type, store.nickname()));
                } else if (input.type.equalsIgnoreCase("hidden")) {
                    fields.add(input);
                }
            }
            if (cancelled.get() || !connected.getAsBoolean()) {
                return;
            }
            HttpRequest submission = HttpRequest.newBuilder(action)
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encodeForm(fields)))
                    .build();
            HttpResponse<String> response = request(submission);
            String body = response.body();
            logResponse(response, body);
            if (hasChallenge(body)) {
                log("CAPTCHA при отправке на " + site.name() + ". Внутриигровая отправка недоступна.");
            } else if (isSuccessful(response) && confirmed(body)) {
                store.setVoted(index, true);
                log("Сайт подтвердил голос: " + site.name());
            } else {
                log("Сайт не подтвердил голос: " + site.name() + ".");
            }
            return;
        }
        log("Подходящая форма не найдена: " + site.name() + ". Внутриигровая отправка недоступна.");
    }

    private HttpResponse<String> request(HttpRequest request) throws IOException {
        log("HTTP " + request.method() + " " + request.uri());
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP-запрос прерван", e);
        }
    }

    private void logResponse(HttpResponse<String> response, String body) {
        log("HTTP " + response.statusCode() + " " + response.uri());
        log("Тело ответа (первые 500 символов): " + body.substring(0, Math.min(500, body.length())).replaceAll("\\s+", " "));
    }

    private boolean isSuccessful(HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private boolean hasChallenge(String body) {
        String text = body.toLowerCase(Locale.ROOT);
        return text.contains("captcha") || text.contains("cf-chl") || text.contains("turnstile")
                || text.contains("just a moment") || text.contains("cloudflare challenge");
    }

    private boolean confirmed(String body) throws IOException {
        String text = htmlText(body).toLowerCase(Locale.ROOT);
        return text.contains("thank you for voting") || text.contains("vote has been recorded")
                || text.contains("your vote was successful") || text.contains("ваш голос учтён");
    }

    private List<HtmlForm> parseForms(String html) throws IOException {
        List<HtmlForm> forms = new ArrayList<>();
        HtmlForm[] currentForm = new HtmlForm[1];
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (HTML.Tag.FORM.equals(tag)) {
                    currentForm[0] = new HtmlForm(attribute(attributes, HTML.Attribute.METHOD),
                            attribute(attributes, HTML.Attribute.ACTION));
                    forms.add(currentForm[0]);
                } else if (HTML.Tag.INPUT.equals(tag)) {
                    addInput(attributes);
                }
            }

            @Override
            public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attributes, int position) {
                if (HTML.Tag.INPUT.equals(tag)) {
                    addInput(attributes);
                }
            }

            @Override
            public void handleEndTag(HTML.Tag tag, int position) {
                if (HTML.Tag.FORM.equals(tag)) {
                    currentForm[0] = null;
                }
            }

            private void addInput(MutableAttributeSet attributes) {
                if (currentForm[0] != null) {
                    String name = attribute(attributes, HTML.Attribute.NAME);
                    if (!name.isEmpty()) {
                        currentForm[0].inputs.add(new FormInput(name,
                                attribute(attributes, HTML.Attribute.TYPE),
                                attribute(attributes, HTML.Attribute.VALUE)));
                    }
                }
            }
        }, true);
        return forms;
    }

    private String htmlText(String html) throws IOException {
        StringBuilder text = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override
            public void handleText(char[] data, int position) {
                text.append(data).append(' ');
            }
        }, true);
        return text.toString();
    }

    private static String attribute(MutableAttributeSet attributes, HTML.Attribute name) {
        Object value = attributes.getAttribute(name);
        return value == null ? "" : value.toString();
    }

    private static String encodeForm(List<FormInput> fields) {
        StringBuilder body = new StringBuilder();
        for (FormInput field : fields) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(field.name, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(field.value, StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static final class HtmlForm {
        private final String method;
        private final String action;
        private final List<FormInput> inputs = new ArrayList<>();

        private HtmlForm(String method, String action) {
            this.method = method;
            this.action = action;
        }
    }

    private record FormInput(String name, String type, String value) {
    }
}