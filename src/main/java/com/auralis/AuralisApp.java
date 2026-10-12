
package com.auralis;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AuralisApp extends Application {

    // Starts the application and displays the Auralis home screen.
    @Override
    public void start(Stage stage) {
        HomeView homeView = new HomeView();

        Scene scene = new Scene(homeView.createView(), 1200, 750);

        stage.setTitle("Auralis");
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();
    }

    // Launches the JavaFX application.
    public static void main(String[] args) {
        launch(args);
    }
}
