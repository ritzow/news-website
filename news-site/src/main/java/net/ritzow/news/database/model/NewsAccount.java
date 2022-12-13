package net.ritzow.news.database.model;

import io.permazen.annotation.PermazenType;

@PermazenType
public abstract class NewsAccount {
	public abstract byte[] getPwHash();
	public abstract void setPwHash(byte[] hash);
	public abstract byte[] getPwSalt();
	public abstract void setPwSalt(byte[] salt);
	public abstract String getUsername();
	public abstract void setUsername(String username);
}
