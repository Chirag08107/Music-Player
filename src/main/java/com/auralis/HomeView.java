package com.auralis;

import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeView {

    // The landing page always shows exactly this many songs (5 columns x 4 rows).
    private static final int HOME_PAGE_SIZE = 20;
    private static final int COLUMNS = 5;

    private static final String CARD_STYLE = "-fx-background-color: #151515; -fx-background-radius: 16;";
    private static final String CARD_HOVER_STYLE = "-fx-background-color: #202020; -fx-background-radius: 16;";

    private final JamendoCatalog catalog = new JamendoCatalog();
    private final GridPane songGrid = new GridPane();
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label("Loading songs...");
    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(400));

    // Keeps the landing page so the player can return to it.
    private BorderPane homeRoot;

    // The 20 songs shown when the search box is empty.
    private List<Song> browseSongs = new ArrayList<>();

    // The songs currently displayed as cards (also used as the play queue).
    private List<Song> shownSongs = new ArrayList<>();

    private String currentSearch = "";

    // Creates the home view and prepares online search.
    public HomeView() {
        searchDelay.setOnFinished(event -> runSearch());
    }

    // Builds the landing page and starts loading the songs.
    public Parent createView() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0b0b0b;");
        homeRoot = root;

        VBox header = new VBox(18);
        header.setPadding(new Insets(24, 30, 18, 30));
        header.setAlignment(Pos.CENTER);

        Label heading = new Label("YOUR MUSIC");
        heading.setStyle(
                "-fx-text-fill: white;"
                        + "-fx-font-size: 25px;"
                        + "-fx-font-weight: bold;");

        searchField.setPromptText("Search songs or artists...");
        searchField.setMaxWidth(500);
        searchField.setStyle(
                "-fx-background-color: #242424;"
                        + "-fx-text-fill: white;"
                        + "-fx-prompt-text-fill: #999999;"
                        + "-fx-background-radius: 22;"
                        + "-fx-padding: 12 18 12 18;"
                        + "-fx-font-size: 14px;");

        // Waits briefly after typing before searching.
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            currentSearch = newValue == null ? "" : newValue.trim();
            searchDelay.stop();

            if (currentSearch.isEmpty()) {
                showSongs(browseSongs);
            } else {
                setStatus("Searching...");
                searchDelay.playFromStart();
            }
        });

        header.getChildren().addAll(heading, searchField);
        root.setTop(header);

        songGrid.setHgap(22);
        songGrid.setVgap(26);
        songGrid.setPadding(new Insets(14, 34, 30, 34));

        for (int i = 0; i < COLUMNS; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / COLUMNS);
            column.setHgrow(Priority.ALWAYS);
            songGrid.getColumnConstraints().add(column);
        }

        ScrollPane scrollPane = new ScrollPane(songGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle(
                "-fx-background: #0b0b0b;"
                        + "-fx-background-color: #0b0b0b;"
                        + "-fx-control-inner-background: #0b0b0b;");

        statusLabel.setStyle("-fx-text-fill: #9a9a9a; -fx-font-size: 15px;");
        statusLabel.setMouseTransparent(true);

        root.setCenter(new StackPane(scrollPane, statusLabel));

        loadBrowseSongs();
        return root;
    }

    // Loads the most popular tracks and keeps 20 distinct ones for the landing
    // page.
    private void loadBrowseSongs() {
        setStatus("Loading songs...");

        catalog.fetchTracks(0, "").whenComplete((tracks, error) -> Platform.runLater(() -> {
            if (error != null) {
                setStatus("Could not load songs. Check your connection.");
                System.err.println("Jamendo catalog error: " + error.getMessage());
                return;
            }

            browseSongs = pickDistinct(tracks);

            if (currentSearch.isEmpty()) {
                showSongs(browseSongs);
            }
        }));
    }

    // Searches the catalog and shows up to 20 distinct matches.
    private void runSearch() {
        String requested = currentSearch;

        if (requested.isEmpty()) {
            return;
        }

        catalog.fetchTracks(0, requested).whenComplete((tracks, error) -> Platform.runLater(() -> {
            if (!requested.equals(currentSearch)) {
                return;
            }

            if (error != null) {
                setStatus("Search failed. Try again.");
                System.err.println("Jamendo search error: " + error.getMessage());
                return;
            }

            showSongs(pickDistinct(tracks));
        }));
    }

    // Picks at most 20 different songs, preferring one song per artist.
    private List<Song> pickDistinct(List<Song> tracks) {
        Map<String, Song> byArtist = new LinkedHashMap<>();
        Map<String, Song> byTitle = new LinkedHashMap<>();

        for (Song song : tracks) {
            String artist = normalize(song.getArtist());
            String title = normalize(song.getTitle());

            if (title.isEmpty()) {
                continue;
            }

            byTitle.putIfAbsent(artist + "|" + title, song);

            if (!artist.isEmpty()) {
                byArtist.putIfAbsent(artist, song);
            }
        }

        List<Song> result = new ArrayList<>();

        for (Song song : byArtist.values()) {
            if (result.size() >= HOME_PAGE_SIZE) {
                break;
            }
            result.add(song);
        }

        for (Song song : byTitle.values()) {
            if (result.size() >= HOME_PAGE_SIZE) {
                break;
            }
            if (!result.contains(song)) {
                result.add(song);
            }
        }

        return result;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    // Shows the status text, or hides the label when the text is empty.
    private void setStatus(String text) {
        statusLabel.setText(text);
        statusLabel.setVisible(!text.isEmpty());
    }

    // Fills the grid with one card per song (5 per row).
    private void showSongs(List<Song> songs) {
        shownSongs = new ArrayList<>(songs);
        songGrid.getChildren().clear();

        for (int i = 0; i < shownSongs.size() && i < HOME_PAGE_SIZE; i++) {
            songGrid.add(createSongCard(shownSongs.get(i)), i % COLUMNS, i / COLUMNS);
        }

        setStatus(shownSongs.isEmpty() ? "No songs found." : "");
    }

    // Creates a square cover card that bulges out on hover and opens the player on
    // click.
    private VBox createSongCard(Song song) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(12));
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle(CARD_STYLE);
        card.setCursor(Cursor.HAND);

        // The cover is always a square that follows the width of the card.
        StackPane artwork = new StackPane();
        artwork.setStyle("-fx-background-color: #262626; -fx-background-radius: 12;");
        artwork.minHeightProperty().bind(card.widthProperty().subtract(24));
        artwork.prefHeightProperty().bind(card.widthProperty().subtract(24));
        artwork.maxHeightProperty().bind(card.widthProperty().subtract(24));

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(artwork.widthProperty());
        clip.heightProperty().bind(artwork.heightProperty());
        artwork.setClip(clip);

        Label placeholder = new Label("\u266A");
        placeholder.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 42px;");

        ImageView cover = new ImageView();
        cover.setPreserveRatio(false);
        cover.setSmooth(true);
        cover.fitWidthProperty().bind(card.widthProperty().subtract(24));
        cover.fitHeightProperty().bind(card.widthProperty().subtract(24));

        // Small play badge that appears while the card is hovered.
        Circle badgeCircle = new Circle(21, Color.web("#ff4fa3"));
        SVGPath badgeIcon = new SVGPath();
        badgeIcon.setContent("M0,0 L14,8 L0,16 Z");
        badgeIcon.setFill(Color.WHITE);
        badgeIcon.setTranslateX(2);
        StackPane badge = new StackPane(badgeCircle, badgeIcon);
        badge.setMaxSize(42, 42);
        badge.setOpacity(0);
        badge.setMouseTransparent(true);
        StackPane.setAlignment(badge, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(badge, new Insets(0, 10, 10, 0));

        artwork.getChildren().addAll(placeholder, cover, badge);

        String coverUrl = song.getCoverPath();
        if (coverUrl != null && !coverUrl.isBlank()) {
            try {
                Image image = new Image(coverUrl, true);
                cover.setImage(image);
                image.errorProperty().addListener((obs, was, hasError) -> {
                    if (hasError) {
                        cover.setImage(null);
                    }
                });
                image.progressProperty().addListener((obs, was, progress) -> {
                    if (progress.doubleValue() >= 1.0 && !image.isError()) {
                        placeholder.setVisible(false);
                    }
                });
            } catch (IllegalArgumentException exception) {
                cover.setImage(null);
            }
        }

        Label title = new Label(song.getTitle());
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label artist = new Label(song.getArtist());
        artist.setMaxWidth(Double.MAX_VALUE);
        artist.setStyle("-fx-text-fill: #a8a8a8; -fx-font-size: 12px;");

        card.getChildren().addAll(artwork, title, artist);

        // Hover: the card bulges out and glows.
        DropShadow glow = new DropShadow(30, Color.rgb(255, 79, 163, 0.45));

        ScaleTransition enlarge = new ScaleTransition(Duration.millis(170), card);
        enlarge.setToX(1.08);
        enlarge.setToY(1.08);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(170), card);
        shrink.setToX(1.0);
        shrink.setToY(1.0);

        card.setOnMouseEntered(event -> {
            card.toFront();
            shrink.stop();
            enlarge.playFromStart();
            card.setStyle(CARD_HOVER_STYLE);
            card.setEffect(glow);
            badge.setOpacity(1);
        });

        card.setOnMouseExited(event -> {
            enlarge.stop();
            shrink.playFromStart();
            card.setStyle(CARD_STYLE);
            card.setEffect(null);
            badge.setOpacity(0);
        });

        card.setOnMouseClicked(event -> openNowPlaying(song));

        return card;
    }

    // Opens the full-screen player. The clicked song plays first and the
    // remaining cards (in order) become the queue.
    private void openNowPlaying(Song song) {
        Scene scene = homeRoot.getScene();

        if (scene == null) {
            return;
        }

        List<Song> queue = new ArrayList<>(shownSongs);
        int start = queue.indexOf(song);

        if (start > 0) {
            Collections.rotate(queue, -start);
        }

        NowPlayingView playerView = new NowPlayingView(
                catalog,
                queue,
                song,
                () -> scene.setRoot(homeRoot));

        scene.setRoot(playerView.createView());
    }
}