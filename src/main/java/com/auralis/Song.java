
package com.auralis;

public class Song {
    private final String id;
    private final String title;
    private final String artist;
    private final String coverPath;
    private final String audioPath;
    private final int duration;

    // Creates a song object using the information received from the catalog.
    public Song(String id, String title, String artist,
            String coverPath, String audioPath, int duration) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.coverPath = coverPath;
        this.audioPath = audioPath;
        this.duration = duration;
    }

    // Returns the unique ID of the song.
    public String getId() {
        return id;
    }

    // Returns the song title.
    public String getTitle() {
        return title;
    }

    // Returns the artist name.
    public String getArtist() {
        return artist;
    }

    // Returns the URL of the album cover.
    public String getCoverPath() {
        return coverPath;
    }

    // Returns the URL used to stream the song.
    public String getAudioPath() {
        return audioPath;
    }

    // Returns the song duration in seconds.
    public int getDuration() {
        return duration;
    }
}
