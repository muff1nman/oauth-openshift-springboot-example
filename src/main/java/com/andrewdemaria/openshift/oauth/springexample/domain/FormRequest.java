package com.andrewdemaria.openshift.oauth.springexample.domain;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class FormRequest {
    @NotNull
    @Size(min = 2, max = 100, message = "Must not be empty")
    private String favoriteColor;

    @NotNull
    @Size(min = 4, max = 100, message = "Must not be empty")
    private String favoriteAnimal;

    public String getFavoriteColor() {
        return favoriteColor;
    }

    public void setFavoriteColor(String favoriteColor) {
        this.favoriteColor = favoriteColor;
    }

    public String getFavoriteAnimal() {
        return favoriteAnimal;
    }

    public void setFavoriteAnimal(String favoriteAnimal) {
        this.favoriteAnimal = favoriteAnimal;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FormRequest{");
        sb.append("favoriteColor='").append(favoriteColor).append('\'');
        sb.append(", favoriteAnimal='").append(favoriteAnimal).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
