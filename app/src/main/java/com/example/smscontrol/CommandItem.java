package com.example.smscontrol;

public class CommandItem {
    private String icon;
    private String label;
    private String description;
    private String command;
    private String commandKey;
    private boolean toggleable;
    private boolean currentState;
    private boolean isLoading;
    private boolean isDisabled;

    public CommandItem(String icon, String label, String description, String command, String commandKey, boolean toggleable, boolean initialState) {
        this.icon = icon;
        this.label = label;
        this.description = description;
        this.command = command;
        this.commandKey = commandKey;
        this.toggleable = toggleable;
        this.currentState = initialState;
        this.isLoading = false;
        this.isDisabled = false;
    }

    // Getters and Setters
    public String getIcon() {
        return isDisabled ? "❌" : icon;
    }

    public String getOriginalIcon() {
        return icon;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCommand() { return command; }

    public String getCommandBase() {
        return command;
    }

    public void setOn(boolean state) {
        this.currentState = state;
    }

    public void setSubtitle(String subtitle) {
        this.description = subtitle;
    }

    public String getCommandKey() { return commandKey; }
    public boolean isToggleable() { return toggleable && !isDisabled; }
    public boolean getCurrentState() { return currentState; }
    public void setCurrentState(boolean state) { this.currentState = state; }
    public boolean isLoading() { return isLoading; }
    public void setLoading(boolean loading) { this.isLoading = loading; }
    public boolean isDisabled() { return isDisabled; }
    public void setDisabled(boolean disabled) { this.isDisabled = disabled; }

    // Compatibility methods
    public String getTitle() { return label; }
    public String getSubtitle() { return description; }
    public String getKey() { return commandKey; }
    public boolean isOn() { return currentState; }
}