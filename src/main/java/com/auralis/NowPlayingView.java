package com.auralis;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NowPlayingView {

    private static final int UP_NEXT_LIMIT = 12;
    private static final int SEARCH_LIMIT = 15;
    private static final int BANDS = 64;

    private static final double RING_SIZE = 250;
    private static final double RING_RADIUS = 108;
    private static final double VISUALIZER_HEIGHT = 120;

    private static final String ACCENT = "#ff4fa3";

    // Simple vector icons (no image files needed).
    private static final String ICON_PLAY = "M0,0 L24,15 L0,30 Z";
    private static final String ICON_PAUSE = "M0,0 H8 V30 H0 Z M16,0 H24 V30 H16 Z";
    private static final String ICON_NEXT = "M0,0 L20,15 L0,30 Z M24,0 H30 V30 H24 Z";
    private static final String ICON_PREVIOUS = "M30,0 L10,15 L30,30 Z M0,0 H6 V30 H0 Z";
    private static final String ICON_BACK = "M16,2 L4,13 L16,24";

    private final JamendoCatalog catalog;
    private final List<Song> queue = new ArrayList<>();
    private final Runnable backAction;

    private final TextField searchField = new TextField();
    private final VBox searchResultsBox = new VBox(6);
    private final VBox searchPanel = new VBox();
    private final VBox upNextBox = new VBox(8);

    private final Label titleLabel = new Label("");
    private final Label artistLabel = new Label("");
    private final Label timeLabel = new Label("0:00 / 0:00");
    private final Label statusLabel = new Label("");

    private final Button playPauseButton = new Button();
    private final Button previousButton = new Button();
    private final Button nextButton = new Button();
    private final Button backButton = new Button();

    private final StackPane stage = new StackPane();
    private final ImageView coverView = new ImageView();
    private final Canvas visualizer = new Canvas(900, VISUALIZER_HEIGHT);

    private final Arc progressArc = new Arc(RING_SIZE / 2, RING_SIZE / 2,
            RING_RADIUS, RING_RADIUS, 90, 0);
    private final Circle knob = new Circle(RING_SIZE / 2, RING_SIZE / 2 - RING_RADIUS, 9);

    // Spectrum data: the target comes from the audio, the level is the smoothed
    // value drawn.
    private final float[] targets = new float[BANDS];
    private final double[] levels = new double[BANDS];
    private AnimationTimer visualizerTimer;
    private LinearGradient barGradient;
    private double gradientWidth = -1;

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(400));
    private final PauseTransition skipDelay = new PauseTransition(Duration.millis(1500));

    private MediaPlayer player;
    private Song currentSong;
    private int currentIndex = -1;
    private boolean playing;
    private int failures;

    // Creates the player screen. The selected song plays first, the rest is the
    // queue.
    public NowPlayingView(JamendoCatalog catalog, List<Song> initialQueue,
            Song selectedSong, Runnable backAction) {
        this.catalog = catalog;
        this.backAction = backAction;

        if (initialQueue != null) {
            queue.addAll(initialQueue);
        }

        if (selectedSong != null && findIndex(selectedSong) < 0) {
            queue.add(0, selectedSong);
        }

        currentIndex = selectedSong == null ? -1 : findIndex(selectedSong);

        searchDelay.setOnFinished(event -> runSearch());
        skipDelay.setOnFinished(event -> playNext());
    }

    // Builds the whole player screen and starts the selected song.
    public Parent createView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #09090c;");
        layout.setTop(createHeader());
        layout.setCenter(createStage());
        layout.setRight(createUpNextPanel());

        createSearchPanel();

        StackPane page = new StackPane(layout, searchPanel);
        page.setStyle("-fx-background-color: #09090c;");

        startVisualizerTimer();
        refreshUpNext();

        if (currentIndex >= 0) {
            playSong(queue.get(currentIndex));
        }

        return page;
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    // Top bar: back arrow on the left and the search bar in the middle.
    private BorderPane createHeader() {
        SVGPath arrow = new SVGPath();
        arrow.setContent(ICON_BACK);
        arrow.setFill(Color.TRANSPARENT);
        arrow.setStroke(Color.WHITE);
        arrow.setStrokeWidth(3);
        arrow.setStrokeLineCap(StrokeLineCap.ROUND);
        arrow.setStrokeLineJoin(StrokeLineJoin.ROUND);

        backButton.setGraphic(arrow);
        backButton.setCursor(Cursor.HAND);
        backButton.setStyle(
                "-fx-background-color: #1c1c22; -fx-background-radius: 30;"
                        + "-fx-min-width: 42px; -fx-min-height: 42px;"
                        + "-fx-max-width: 42px; -fx-max-height: 42px;");
        backButton.setOnAction(event -> {
            shutdown();
            if (backAction != null) {
                backAction.run();
            }
        });
        addHoverScale(backButton, 1.1);

        searchField.setPromptText("Search for Songs, Artists...");
        searchField.setPrefWidth(480);
        searchField.setMaxWidth(480);
        searchField.setStyle(
                "-fx-background-color: #1c1c24; -fx-text-fill: white;"
                        + "-fx-prompt-text-fill: #8d8d9a; -fx-background-radius: 24;"
                        + "-fx-padding: 11 20 11 20; -fx-font-size: 13px;");

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim();
            searchDelay.stop();

            if (query.isEmpty()) {
                hideSearch();
            } else {
                showSearchMessage("Searching...");
                searchDelay.playFromStart();
            }
        });

        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                searchField.clear();
            }
        });

        // Invisible spacer keeps the search bar centered.
        Region balance = new Region();
        balance.setPrefWidth(42);
        balance.setMinWidth(42);

        BorderPane header = new BorderPane();
        header.setPadding(new Insets(14, 22, 14, 22));
        header.setStyle("-fx-background-color: #09090c;");
        header.setLeft(backButton);
        header.setCenter(searchField);
        header.setRight(balance);
        BorderPane.setAlignment(searchField, Pos.CENTER);
        BorderPane.setAlignment(backButton, Pos.CENTER_LEFT);

        return header;
    }

    // Centre area: cover as background, circular player in the middle, visualizer
    // at the bottom.
    private StackPane createStage() {
        stage.setMinSize(0, 0);
        stage.setStyle("-fx-background-color: #101014;");

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(stage.widthProperty());
        clip.heightProperty().bind(stage.heightProperty());
        stage.setClip(clip);

        // The cover fills the whole area (cropped, not stretched).
        coverView.setManaged(false);
        coverView.setPreserveRatio(false);
        coverView.setSmooth(true);
        coverView.setEffect(new ColorAdjust(0, -0.05, -0.22, 0));
        stage.widthProperty().addListener((obs, oldValue, newValue) -> fitCover());
        stage.heightProperty().addListener((obs, oldValue, newValue) -> {
            fitCover();
            visualizer.setLayoutY(newValue.doubleValue() - VISUALIZER_HEIGHT);
        });

        // Dark gradient so the controls and text stay readable on any cover.
        Region shade = new Region();
        shade.setStyle("-fx-background-color: linear-gradient(to bottom,"
                + " rgba(0,0,0,0.30) 0%, rgba(0,0,0,0.12) 45%, rgba(0,0,0,0.88) 100%);");

        // Visualizer sits on the bottom edge and follows the width of the stage.
        visualizer.setManaged(false);
        visualizer.setMouseTransparent(true);
        visualizer.widthProperty().bind(stage.widthProperty());
        visualizer.setEffect(new DropShadow(18, Color.web(ACCENT, 0.55)));

        // Previous / ring with play-pause / next.
        styleTransportButton(previousButton, ICON_PREVIOUS, 1.0);
        styleTransportButton(nextButton, ICON_NEXT, 1.0);
        previousButton.setOnAction(event -> playPrevious());
        nextButton.setOnAction(event -> playNext());

        HBox transport = new HBox(44, previousButton, createRing(), nextButton);
        transport.setAlignment(Pos.CENTER);

        timeLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(560);
        titleLabel.setTextAlignment(TextAlignment.CENTER);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        titleLabel.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.8)));

        artistLabel.setStyle("-fx-text-fill: #dcdce6; -fx-font-size: 14px;");
        artistLabel.setEffect(new DropShadow(8, Color.rgb(0, 0, 0, 0.8)));

        statusLabel.setStyle("-fx-text-fill: #b9b9c6; -fx-font-size: 11px;");

        VBox content = new VBox(14, transport, timeLabel, titleLabel, artistLabel, statusLabel);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(0, 0, 100, 0));

        stage.getChildren().addAll(coverView, shade, content, visualizer);
        return stage;
    }

    // The circular progress ring with the play/pause button in its centre.
    private StackPane createRing() {
        double c = RING_SIZE / 2;

        Pane ringPane = new Pane();
        ringPane.setMinSize(RING_SIZE, RING_SIZE);
        ringPane.setPrefSize(RING_SIZE, RING_SIZE);
        ringPane.setMaxSize(RING_SIZE, RING_SIZE);

        Circle disc = new Circle(c, c, RING_RADIUS - 10, Color.rgb(0, 0, 0, 0.28));

        Circle track = new Circle(c, c, RING_RADIUS);
        track.setFill(Color.TRANSPARENT);
        track.setStroke(Color.rgb(255, 255, 255, 0.30));
        track.setStrokeWidth(6);

        progressArc.setType(ArcType.OPEN);
        progressArc.setFill(Color.TRANSPARENT);
        progressArc.setStroke(Color.web(ACCENT));
        progressArc.setStrokeWidth(6);
        progressArc.setStrokeLineCap(StrokeLineCap.ROUND);
        progressArc.setMouseTransparent(true);

        knob.setFill(Color.web(ACCENT));
        knob.setEffect(new DropShadow(12, Color.web(ACCENT, 0.9)));
        knob.setMouseTransparent(true);

        ringPane.getChildren().addAll(disc, track, progressArc, knob);

        // Clicking on the ring jumps to that position in the song.
        ringPane.setOnMouseClicked(event -> seekFromRing(event.getX(), event.getY()));

        styleTransportButton(playPauseButton, ICON_PAUSE, 1.35);
        playPauseButton.setOnAction(event -> togglePlayback());

        StackPane ring = new StackPane(ringPane, playPauseButton);
        ring.setMinSize(RING_SIZE, RING_SIZE);
        ring.setPrefSize(RING_SIZE, RING_SIZE);
        ring.setMaxSize(RING_SIZE, RING_SIZE);
        return ring;
    }

    // Right side: the "Next Up" queue.
    private VBox createUpNextPanel() {
        Label heading = new Label("Next Up");
        heading.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");

        ScrollPane scroll = new ScrollPane(upNextBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox panel = new VBox(12, heading, scroll);
        panel.setPadding(new Insets(16, 14, 16, 14));
        panel.setPrefWidth(290);
        panel.setMinWidth(290);
        panel.setMaxWidth(290);
        panel.setStyle("-fx-background-color: #101015;");
        return panel;
    }

    // Drop-down list that appears under the search bar.
    private void createSearchPanel() {
        ScrollPane scroll = new ScrollPane(searchResultsBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(360);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        searchPanel.getChildren().add(scroll);
        searchPanel.setPadding(new Insets(8));
        searchPanel.setPrefWidth(520);
        searchPanel.setMaxWidth(520);
        searchPanel.setMaxHeight(Region.USE_PREF_SIZE);
        searchPanel.setStyle(
                "-fx-background-color: #16161d; -fx-background-radius: 16;"
                        + "-fx-border-color: #2a2a35; -fx-border-radius: 16;");
        searchPanel.setEffect(new DropShadow(24, Color.rgb(0, 0, 0, 0.7)));
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);

        StackPane.setAlignment(searchPanel, Pos.TOP_CENTER);
        StackPane.setMargin(searchPanel, new Insets(66, 0, 0, 0));
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private void runSearch() {
        String requested = searchField.getText() == null ? "" : searchField.getText().trim();

        if (requested.isEmpty()) {
            return;
        }

        catalog.fetchTracks(0, requested).whenComplete((tracks, error) -> Platform.runLater(() -> {
            String now = searchField.getText() == null ? "" : searchField.getText().trim();

            if (!requested.equals(now)) {
                return;
            }

            if (error != null) {
                showSearchMessage("Search failed. Check your connection.");
                return;
            }

            searchResultsBox.getChildren().clear();
            int shown = 0;

            for (Song song : tracks) {
                searchResultsBox.getChildren().add(createSongRow(song, () -> playFromSearch(song)));

                if (++shown >= SEARCH_LIMIT) {
                    break;
                }
            }

            if (shown == 0) {
                showSearchMessage("No songs found.");
            }
        }));
    }

    private void showSearchMessage(String text) {
        Label message = new Label(text);
        message.setPadding(new Insets(12));
        message.setStyle("-fx-text-fill: #9a9aa8; -fx-font-size: 13px;");
        searchResultsBox.getChildren().setAll(message);
        searchPanel.setManaged(true);
        searchPanel.setVisible(true);
        searchPanel.toFront();
    }

    private void hideSearch() {
        searchPanel.setVisible(false);
        searchPanel.setManaged(false);
        searchResultsBox.getChildren().clear();
    }

    // Puts the chosen search result right after the current song and plays it.
    private void playFromSearch(Song song) {
        searchField.clear();

        Song playingNow = currentSong;
        queue.removeIf(item -> item.getId().equals(song.getId()));

        int position = playingNow == null
                ? queue.size()
                : Math.max(0, findIndex(playingNow) + 1);

        queue.add(position, song);
        playSong(song);
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    private void playSong(Song song) {
        if (song == null) {
            return;
        }

        skipDelay.stop();
        releasePlayer();

        currentSong = song;
        currentIndex = findIndex(song);

        titleLabel.setText(song.getTitle());
        artistLabel.setText(song.getArtist());
        timeLabel.setText("0:00 / " + formatTime(song.getDuration()));
        updateProgress(0);
        setCover(song);
        refreshUpNext();

        if (song.getAudioPath() == null || song.getAudioPath().isBlank()) {
            handleStreamError("No audio stream is available for this track.");
            return;
        }

        showPauseIcon(true);
        statusLabel.setText("Loading...");

        try {
            Media media = new Media(song.getAudioPath());
            MediaPlayer mp = new MediaPlayer(media);
            player = mp;

            // Live audio analysis for the visualizer.
            mp.setAudioSpectrumNumBands(BANDS);
            mp.setAudioSpectrumInterval(0.04);
            mp.setAudioSpectrumThreshold(-60);
            mp.setAudioSpectrumListener((timestamp, interval, magnitudes, phases) -> {
                if (player == mp) {
                    feedSpectrum(magnitudes);
                }
            });

            mp.setOnReady(() -> {
                if (player == mp) {
                    mp.play();
                }
            });

            mp.setOnPlaying(() -> {
                if (player != mp) {
                    return;
                }
                playing = true;
                failures = 0;
                statusLabel.setText("");
                showPauseIcon(true);
            });

            mp.setOnPaused(() -> {
                if (player != mp) {
                    return;
                }
                playing = false;
                showPauseIcon(false);
            });

            mp.setOnStalled(() -> {
                if (player == mp) {
                    statusLabel.setText("Buffering...");
                }
            });

            // When a song ends, the next one in the queue starts automatically.
            mp.setOnEndOfMedia(() -> {
                if (player == mp) {
                    Platform.runLater(this::playNext);
                }
            });

            mp.setOnError(() -> {
                if (player == mp) {
                    System.err.println("Playback error: " + mp.getError());
                    handleStreamError("This track could not be played. Skipping...");
                }
            });

            mp.currentTimeProperty().addListener((observable, oldTime, newTime) -> {
                if (player != mp) {
                    return;
                }

                double seconds = newTime.toSeconds();
                Duration total = mp.getTotalDuration();
                double totalSeconds = song.getDuration();

                if (total != null && !total.isUnknown() && !total.isIndefinite()) {
                    totalSeconds = total.toSeconds();
                }

                timeLabel.setText(formatTime(seconds) + " / " + formatTime(totalSeconds));

                if (totalSeconds > 0) {
                    updateProgress(seconds / totalSeconds);
                }
            });
        } catch (RuntimeException exception) {
            System.err.println("Stream error: " + exception.getMessage());
            handleStreamError("Unable to start this stream. Skipping...");
        }
    }

    // Shows an error and moves on to the next song (unless every song keeps
    // failing).
    private void handleStreamError(String message) {
        playing = false;
        showPauseIcon(false);
        failures++;

        if (failures <= queue.size()) {
            statusLabel.setText(message);
            skipDelay.playFromStart();
        } else {
            statusLabel.setText("Could not play any song. Check your connection.");
        }
    }

    // Plays the next song in the queue; after the last one it starts again from the
    // top.
    private void playNext() {
        if (queue.isEmpty()) {
            return;
        }

        int next = (currentIndex + 1) % queue.size();
        playSong(queue.get(next));
    }

    // Restarts the song if it is past 3 seconds, otherwise goes to the previous
    // song.
    private void playPrevious() {
        if (queue.isEmpty()) {
            return;
        }

        if (player != null && player.getCurrentTime().toSeconds() > 3) {
            player.seek(Duration.ZERO);
            return;
        }

        int previous = (currentIndex - 1 + queue.size()) % queue.size();
        playSong(queue.get(previous));
    }

    private void togglePlayback() {
        if (player == null) {
            if (currentSong != null) {
                failures = 0;
                playSong(currentSong);
            }
        } else if (playing) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void seekFromRing(double x, double y) {
        if (player == null) {
            return;
        }

        Duration total = player.getTotalDuration();

        if (total == null || total.isUnknown() || total.isIndefinite()) {
            return;
        }

        double center = RING_SIZE / 2;
        double dx = x - center;
        double dy = y - center;

        // Only react to clicks close to the ring itself.
        if (Math.abs(Math.hypot(dx, dy) - RING_RADIUS) > 24) {
            return;
        }

        double angle = Math.atan2(dx, -dy);

        if (angle < 0) {
            angle += 2 * Math.PI;
        }

        player.seek(total.multiply(angle / (2 * Math.PI)));
    }

    private void releasePlayer() {
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            old.setAudioSpectrumListener(null);
            old.stop();
            old.dispose();
        }

        playing = false;
        Arrays.fill(targets, 0f);
    }

    // Stops music, timers and animation when leaving the player.
    private void shutdown() {
        skipDelay.stop();
        searchDelay.stop();
        releasePlayer();

        if (visualizerTimer != null) {
            visualizerTimer.stop();
        }
    }

    // ------------------------------------------------------------------
    // Cover and progress
    // ------------------------------------------------------------------

    // Loads the song cover and shows it as the full background.
    private void setCover(Song song) {
        String url = song.getCoverPath();

        if (url == null || url.isBlank()) {
            coverView.setImage(null);
            return;
        }

        try {
            Image image = new Image(url, true);

            Runnable apply = () -> {
                if (currentSong != song || image.isError()) {
                    return;
                }
                coverView.setImage(image);
                fitCover();
                FadeTransition fade = new FadeTransition(Duration.millis(450), coverView);
                fade.setFromValue(0);
                fade.setToValue(1);
                fade.play();
            };

            if (image.getProgress() >= 1.0) {
                apply.run();
            } else {
                image.progressProperty().addListener((observable, oldValue, progress) -> {
                    if (progress.doubleValue() >= 1.0) {
                        apply.run();
                    }
                });
            }
        } catch (IllegalArgumentException exception) {
            coverView.setImage(null);
        }
    }

    // Scales and crops the cover so it fills the area without stretching.
    private void fitCover() {
        Image image = coverView.getImage();
        double w = stage.getWidth();
        double h = stage.getHeight();

        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0 || w <= 0 || h <= 0) {
            coverView.setViewport(null);
            return;
        }

        double scale = Math.max(w / image.getWidth(), h / image.getHeight());
        double viewWidth = w / scale;
        double viewHeight = h / scale;

        coverView.setViewport(new Rectangle2D(
                (image.getWidth() - viewWidth) / 2,
                (image.getHeight() - viewHeight) / 2,
                viewWidth,
                viewHeight));
        coverView.setFitWidth(w);
        coverView.setFitHeight(h);
    }

    // Moves the pink arc and the knob; progress is a value from 0 to 1.
    private void updateProgress(double progress) {
        double p = Math.max(0, Math.min(1, progress));
        double center = RING_SIZE / 2;
        double angle = p * 2 * Math.PI;

        progressArc.setLength(-360 * p);
        knob.setCenterX(center + RING_RADIUS * Math.sin(angle));
        knob.setCenterY(center - RING_RADIUS * Math.cos(angle));
    }

    // ------------------------------------------------------------------
    // Visualizer
    // ------------------------------------------------------------------

    // Converts the spectrum (in dB) to values between 0 and 1.
    private void feedSpectrum(float[] magnitudes) {
        if (magnitudes == null) {
            return;
        }

        int count = Math.min(magnitudes.length, BANDS);

        for (int i = 0; i < count; i++) {
            double value = (magnitudes[i] + 60.0) / 60.0;
            value = Math.max(0, Math.min(1, value));
            targets[i] = (float) Math.min(1.0, Math.pow(value, 1.15) * 1.2);
        }
    }

    private void startVisualizerTimer() {
        visualizerTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawVisualizer();
            }
        };
        visualizerTimer.start();
    }

    // Draws mirrored bars: the bass is in the middle and the high notes at the
    // edges.
    private void drawVisualizer() {
        double width = visualizer.getWidth();
        double height = visualizer.getHeight();
        GraphicsContext g = visualizer.getGraphicsContext2D();

        g.clearRect(0, 0, width, height);

        if (width < 20) {
            return;
        }

        for (int i = 0; i < BANDS; i++) {
            double target = playing ? targets[i] : 0;

            // Rises fast, falls slowly - looks smooth and lively.
            double speed = target > levels[i] ? 0.6 : 0.12;
            levels[i] += (target - levels[i]) * speed;
        }

        if (barGradient == null || gradientWidth != width) {
            barGradient = new LinearGradient(0, 0, width, 0, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#ff4fa3")),
                    new Stop(0.25, Color.web("#b34dff")),
                    new Stop(0.5, Color.web("#4d8dff")),
                    new Stop(0.75, Color.web("#b34dff")),
                    new Stop(1.0, Color.web("#ff4fa3")));
            gradientWidth = width;
        }

        g.setFill(barGradient);

        int bars = BANDS * 2;
        double gap = 3;
        double barWidth = Math.max(2, (width - gap * (bars - 1)) / bars);

        for (int k = 0; k < bars; k++) {
            int band = k < BANDS ? BANDS - 1 - k : k - BANDS;
            double barHeight = Math.max(3, levels[band] * height * 0.95);
            double x = k * (barWidth + gap);

            g.fillRoundRect(x, height - barHeight, barWidth, barHeight, barWidth, barWidth);
        }
    }

    // ------------------------------------------------------------------
    // Next Up list and small helpers
    // ------------------------------------------------------------------

    // Shows the songs that will play next (it wraps around to the start of the
    // queue).
    private void refreshUpNext() {
        upNextBox.getChildren().clear();

        int shown = 0;

        for (int step = 1; step < queue.size() && shown < UP_NEXT_LIMIT; step++) {
            Song song = queue.get((Math.max(currentIndex, 0) + step) % queue.size());
            upNextBox.getChildren().add(createSongRow(song, () -> playSong(song)));
            shown++;
        }

        if (shown == 0) {
            Label empty = new Label("No upcoming songs");
            empty.setStyle("-fx-text-fill: #8d8d9a;");
            upNextBox.getChildren().add(empty);
        }
    }

    // One row: small cover, title, artist and length. Clicking it runs the action.
    private HBox createSongRow(Song song, Runnable onClick) {
        String normalStyle = "-fx-background-color: transparent; -fx-background-radius: 10;";
        String hoverStyle = "-fx-background-color: #1d1d26; -fx-background-radius: 10;";

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(6));
        row.setCursor(Cursor.HAND);
        row.setStyle(normalStyle);

        ImageView thumb = new ImageView();
        thumb.setFitWidth(46);
        thumb.setFitHeight(46);
        thumb.setPreserveRatio(false);
        thumb.setSmooth(true);

        String url = song.getCoverPath();
        if (url != null && !url.isBlank()) {
            try {
                thumb.setImage(new Image(url, 92, 92, false, true, true));
            } catch (IllegalArgumentException exception) {
                thumb.setImage(null);
            }
        }

        Rectangle round = new Rectangle(46, 46);
        round.setArcWidth(12);
        round.setArcHeight(12);
        thumb.setClip(round);

        Label title = new Label(song.getTitle());
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        Label artist = new Label(song.getArtist());
        artist.setMaxWidth(Double.MAX_VALUE);
        artist.setStyle("-fx-text-fill: #8d8d9a; -fx-font-size: 11px;");

        VBox text = new VBox(3, title, artist);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label length = new Label(formatTime(song.getDuration()));
        length.setStyle("-fx-text-fill: #8d8d9a; -fx-font-size: 11px;");

        row.getChildren().addAll(thumb, text, length);
        row.setOnMouseEntered(event -> row.setStyle(hoverStyle));
        row.setOnMouseExited(event -> row.setStyle(normalStyle));
        row.setOnMouseClicked(event -> onClick.run());
        return row;
    }

    private void styleTransportButton(Button button, String iconPath, double scale) {
        button.setGraphic(createIcon(iconPath, scale));
        button.setCursor(Cursor.HAND);
        button.setFocusTraversable(false);
        button.setStyle("-fx-background-color: transparent; -fx-padding: 16;");
        addHoverScale(button, 1.12);
    }

    private SVGPath createIcon(String content, double scale) {
        SVGPath icon = new SVGPath();
        icon.setContent(content);
        icon.setFill(Color.WHITE);
        icon.setScaleX(scale);
        icon.setScaleY(scale);
        return icon;
    }

    // Shows the pause icon while playing, and the play icon while paused.
    private void showPauseIcon(boolean showPause) {
        playPauseButton.setGraphic(createIcon(showPause ? ICON_PAUSE : ICON_PLAY, 1.35));
    }

    private void addHoverScale(Node node, double scale) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(120), node);
        grow.setToX(scale);
        grow.setToY(scale);

        ScaleTransition back = new ScaleTransition(Duration.millis(120), node);
        back.setToX(1.0);
        back.setToY(1.0);

        node.setOnMouseEntered(event -> {
            back.stop();
            grow.playFromStart();
        });
        node.setOnMouseExited(event -> {
            grow.stop();
            back.playFromStart();
        });
    }

    private int findIndex(Song song) {
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(song.getId())) {
                return i;
            }
        }
        return -1;
    }

    private String formatTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            return "0:00";
        }

        int total = (int) seconds;
        return String.format("%d:%02d", total / 60, total % 60);
    }
}