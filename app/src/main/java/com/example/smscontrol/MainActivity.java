package com.example.smscontrol;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.widget.TimePicker;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String CHANNEL_ID = "sms_control_alerts";
    private EditText phoneNumberInput;
    private TextView statusBadge, statusDescriptionText, lastUpdateText, boardTimeText;
    private RecyclerView commandsRecyclerView, messagesRecyclerView;
    private CommandAdapter commandAdapter;
    private MessageAdapter messageAdapter;
    private final List<CommandItem> commandList = new ArrayList<>();
    private final List<MessageItem> messageList = new ArrayList<>();
    private SharedPreferences prefs;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Handler liveClockHandler = new Handler(Looper.getMainLooper());
    private Runnable liveClockRunnable;
    private CommandItem pendingCommand = null;
    private long lastResponseTime = 0;
    private int failedAttempts = 0;
    private boolean autoRefreshEnabled = false;
    private Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;

    private Map<String, Boolean> deviceStates = new HashMap<>();
    private Map<String, String> alarmInfo = new HashMap<>();

    private final BroadcastReceiver smsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    for (Object pdu : pdus) {
                        SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
                        String sender = sms.getOriginatingAddress();
                        String messageBody = sms.getMessageBody();
                        String savedNumber = phoneNumberInput.getText().toString().trim();

                        if (sender != null && sender.contains(savedNumber)) {
                            handleDeviceResponse(messageBody);
                        }
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = getSharedPreferences("SMSControlPrefs", MODE_PRIVATE);
        createNotificationChannel();
        initViews();
        setupLists();
        loadChatHistory();
        loadCommandStates();
        loadDisabledCommands();
        checkPermissions();
        registerSmsReceiver();
        setupAutoRefresh();
        updateStatusDisplay();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Sensor Alerts";
            String description = "Motion and door sensor notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void initViews() {
        phoneNumberInput = findViewById(R.id.phoneNumberInput);
        statusBadge = findViewById(R.id.statusBadge);
        statusDescriptionText = findViewById(R.id.statusDescriptionText);
        lastUpdateText = findViewById(R.id.lastUpdateText);
        boardTimeText = findViewById(R.id.boardTimeText);
        commandsRecyclerView = findViewById(R.id.commandsRecyclerView);
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        Button savePhoneButton = findViewById(R.id.savePhoneButton);

        String savedNumber = prefs.getString("phoneNumber", "");
        phoneNumberInput.setText(savedNumber);
        updateStatusUI(savedNumber);

        savePhoneButton.setOnClickListener(v -> {
            String number = phoneNumberInput.getText().toString().trim();
            prefs.edit().putString("phoneNumber", number).apply();
            updateStatusUI(number);
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(this::sendRefreshCommand, 1000);
        });

        liveClockRunnable = new Runnable() {
            @Override
            public void run() {
                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                lastUpdateText.setText("Current Time: " + currentTime);
                liveClockHandler.postDelayed(this, 1000);
            }
        };
        liveClockHandler.post(liveClockRunnable);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem autoRefreshItem = menu.findItem(R.id.action_auto_refresh);
        if (autoRefreshItem != null) {
            autoRefreshEnabled = prefs.getBoolean("auto_refresh", false);
            autoRefreshItem.setChecked(autoRefreshEnabled);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh_all) {
            sendRefreshCommand();
            return true;
        } else if (id == R.id.action_auto_refresh) {
            autoRefreshEnabled = !autoRefreshEnabled;
            item.setChecked(autoRefreshEnabled);
            prefs.edit().putBoolean("auto_refresh", autoRefreshEnabled).apply();
            if (autoRefreshEnabled) {
                startAutoRefresh();
                Toast.makeText(this, "Auto-refresh enabled (every 5 min)", Toast.LENGTH_SHORT).show();
            } else {
                stopAutoRefresh();
                Toast.makeText(this, "Auto-refresh disabled", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_clear_log) {
            showClearLogDialog();
            return true;
        } else if (id == R.id.action_add_command) {
            showAddCommandDialog();
            return true;
        } else if (id == R.id.action_settings) {
            showSettingsDialog();
            return true;
        } else if (id == R.id.action_backup) {
            backupConfiguration();
            return true;
        } else if (id == R.id.action_quick_commands) {
            showQuickCommandsDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupAutoRefresh() {
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (autoRefreshEnabled) {
                    sendRefreshCommand();
                    autoRefreshHandler.postDelayed(this, 300000);
                }
            }
        };
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 300000);
    }

    private void stopAutoRefresh() {
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void sendRefreshCommand() {
        String phone = phoneNumberInput.getText().toString().trim();
        if (phone.isEmpty()) {
            Toast.makeText(this, "Please configure phone number first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (commandList.size() > 0) {
            CommandItem statusCmd = commandList.get(0);
            sendCommand("Status", statusCmd, 0);
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show();
        }
    }

    private void showClearLogDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure?")
                .setPositiveButton("Clear", (d, w) -> {
                    messageList.clear();
                    messageAdapter.notifyDataSetChanged();
                    saveChatHistory();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddCommandDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_command, null);
        EditText iconInput = view.findViewById(R.id.iconInput);
        EditText labelInput = view.findViewById(R.id.labelInput);
        EditText descInput = view.findViewById(R.id.descInput);
        EditText commandInput = view.findViewById(R.id.commandInput);
        EditText keyInput = view.findViewById(R.id.keyInput);
        Switch toggleSwitch = view.findViewById(R.id.toggleSwitch);

        new AlertDialog.Builder(this)
                .setTitle("Add Command")
                .setView(view)
                .setPositiveButton("Add", (d, w) -> {
                    String icon = iconInput.getText().toString().trim();
                    String label = labelInput.getText().toString().trim();
                    String desc = descInput.getText().toString().trim();
                    String command = commandInput.getText().toString().trim();
                    String key = keyInput.getText().toString().trim();
                    boolean toggleable = toggleSwitch.isChecked();

                    if (label.isEmpty() || command.isEmpty()) {
                        Toast.makeText(this, "Label and command are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (icon.isEmpty()) icon = "⚙️";
                    if (key.isEmpty()) key = command.toLowerCase();

                    CommandItem newCmd = new CommandItem(icon, label, desc, command, key, toggleable, false);
                    commandList.add(newCmd);
                    commandAdapter.notifyItemInserted(commandList.size() - 1);
                    saveCommandStates();
                    Toast.makeText(this, "Command added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSettingsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        EditText phoneInput = view.findViewById(R.id.phoneInput);
        EditText timeoutInput = view.findViewById(R.id.timeoutInput);
        Switch vibrateSwitch = view.findViewById(R.id.vibrateSwitch);
        Switch soundSwitch = view.findViewById(R.id.soundSwitch);

        String savedNumber = prefs.getString("phoneNumber", "");
        int timeout = prefs.getInt("timeout", 30);
        boolean vibrate = prefs.getBoolean("vibrate", true);
        boolean sound = prefs.getBoolean("sound", false);

        phoneInput.setText(savedNumber);
        timeoutInput.setText(String.valueOf(timeout));
        vibrateSwitch.setChecked(vibrate);
        soundSwitch.setChecked(sound);

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    try {
                        String newPhone = phoneInput.getText().toString().trim();
                        int newTimeout = Integer.parseInt(timeoutInput.getText().toString());

                        prefs.edit()
                                .putString("phoneNumber", newPhone)
                                .putInt("timeout", newTimeout)
                                .putBoolean("vibrate", vibrateSwitch.isChecked())
                                .putBoolean("sound", soundSwitch.isChecked())
                                .apply();

                        phoneNumberInput.setText(newPhone);
                        updateStatusUI(newPhone);
                        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid value", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showQuickCommandsDialog() {
        CharSequence[] commands = {
                "🔴 Turn Off All",
                "🟢 Turn On All",
                "🔄 Restart",
                "⚠️ Emergency"
        };

        new AlertDialog.Builder(this)
                .setTitle("Quick Commands")
                .setItems(commands, (d, which) -> {
                    String cmd = "";
                    switch (which) {
                        case 0: cmd = "AllOff"; break;
                        case 1: cmd = "AllOn"; break;
                        case 2: cmd = "Restart"; break;
                        case 3: cmd = "Emergency"; break;
                    }
                    if (!cmd.isEmpty()) {
                        CommandItem quickCmd = new CommandItem("⚡", "Quick", cmd, cmd, "quick", false, false);
                        sendCommand(cmd, quickCmd, -1);
                    }
                })
                .show();
    }

    private void backupConfiguration() {
        Map<String, Object> backup = new HashMap<>();
        backup.put("phoneNumber", prefs.getString("phoneNumber", ""));
        backup.put("timestamp", System.currentTimeMillis());

        String json = new Gson().toJson(backup);
        prefs.edit().putString("backup", json).apply();

        Toast.makeText(this, "Backup completed ✓", Toast.LENGTH_LONG).show();
    }

    private void updateStatusDisplay() {
        if (statusDescriptionText == null) return;

        StringBuilder status = new StringBuilder();

        if (deviceStates.containsKey("sensors")) {
            status.append("Sensors: ").append(deviceStates.get("sensors") ? "ENABLED" : "DISABLED").append("\n");
        }

        if (deviceStates.containsKey("alarms")) {
            status.append("Alarms: ").append(deviceStates.get("alarms") ? "ENABLED" : "DISABLED").append("\n");
        }

        for (int i = 1; i <= 3; i++) {
            String key = "l" + i;
            if (deviceStates.containsKey(key)) {
                status.append("LED").append(i).append(": ").append(deviceStates.get(key) ? "ON" : "OFF");
                if (alarmInfo.containsKey(key)) {
                    status.append(" (").append(alarmInfo.get(key)).append(")");
                }
                status.append("\n");
            }
        }

        if (deviceStates.containsKey("water")) {
            status.append("Water: ").append(deviceStates.get("water") ? "ON" : "OFF");
            if (alarmInfo.containsKey("water")) {
                status.append(" (").append(alarmInfo.get("water")).append(")");
            }
            status.append("\n");
        }

        if (deviceStates.containsKey("fan")) {
            status.append("Fan: ").append(deviceStates.get("fan") ? "ON" : "OFF").append("\n");
        }

        if (status.length() == 0) {
            status.append("No status data available yet.\nTap refresh to update.");
        }

        statusDescriptionText.setText(status.toString().trim());
    }

    private void registerSmsReceiver() {
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(smsReceiver, filter);
        }
    }

    private void updateStatusUI(String number) {
        if (statusBadge == null) return;

        if (number.isEmpty()) {
            statusBadge.setText("Not Configured");
            statusBadge.setBackgroundResource(R.drawable.badge_offline);
        } else {
            statusBadge.setText("Connected: " + number);

            long timeSinceResponse = System.currentTimeMillis() - lastResponseTime;
            if (lastResponseTime > 0 && timeSinceResponse < 300000) {
                statusBadge.setBackgroundResource(R.drawable.badge_online);
            } else if (failedAttempts > 2) {
                statusBadge.setBackgroundResource(R.drawable.badge_offline);
            } else {
                statusBadge.setBackgroundResource(R.drawable.badge_idle);
            }
        }
    }

    private void setupLists() {
        commandList.clear();
        commandList.add(new CommandItem("📡", "Status", "System status", "Status", "status", false, false));
        commandList.add(new CommandItem("🚪", "Sensors", "Door & PIR", "Sensors", "sensors", true, false));
        commandList.add(new CommandItem("🛡️", "Alarms", "Alerts", "Alarms", "alarms", true, false));
        commandList.add(new CommandItem("💡", "Led1", "Light 1 / Water Pump", "Led1", "l1", true, false));
        commandList.add(new CommandItem("💡", "Led2", "Light 2", "Led2", "l2", true, false));
        commandList.add(new CommandItem("💡", "Led3", "Light 3", "Led3", "l3", true, false));
        commandList.add(new CommandItem("💧", "Water", "Water Pump", "Water", "water", true, false));
        commandList.add(new CommandItem("⏰", "Alarm1", "LED1 Timer", "Alarm1", "alarm1", false, false));
        commandList.add(new CommandItem("⏰", "Alarm2", "LED2 Timer", "Alarm2", "alarm2", false, false));
        commandList.add(new CommandItem("⏰", "Alarm3", "Water Pump Timer", "Alarm3", "alarm3", false, false));
        commandList.add(new CommandItem("🌡️", "Temperature", "Temp", "Temp", "temp", false, false));
        commandList.add(new CommandItem("💨", "Fan", "Fan", "Fan", "fan", true, false));

        updateDependentButtons(false);

        commandAdapter = new CommandAdapter(commandList, item -> {
            if (item.isLoading() || item.isDisabled()) return;
            int position = commandList.indexOf(item);

            if (item.getCommandKey().startsWith("alarm") && !item.getCommandKey().equals("alarms")) {
                CommandItem alarmsCommand = findCommandByKey("alarms");
                if (alarmsCommand == null || !alarmsCommand.getCurrentState()) {
                    Toast.makeText(this, "Please turn ON Alarms first", Toast.LENGTH_SHORT).show();
                    return;
                }
                showAlarmDialog(item);
            } else if (item.isToggleable()) {
                boolean newState = !item.getCurrentState();
                String cmd = item.getCommandBase() + (newState ? " on" : " off");
                sendCommand(cmd, item, position);
            } else {
                sendCommand(item.getCommandBase(), item, position);
            }
        });

        commandAdapter.setOnLongClickListener((item, position) -> {
            showCommandOptionsDialog(item, position);
        });

        commandsRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        commandsRecyclerView.setAdapter(commandAdapter);

        messageAdapter = new MessageAdapter(messageList);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void updateDependentButtons(boolean alarmsEnabled) {
        String[] dependentKeys = {"alarm1", "alarm2", "alarm3"};
        boolean changed = false;

        for (CommandItem item : commandList) {
            for (String key : dependentKeys) {
                if (item.getCommandKey().equals(key)) {
                    boolean shouldBeDisabled = !alarmsEnabled;
                    if (item.isDisabled() != shouldBeDisabled) {
                        item.setDisabled(shouldBeDisabled);
                        changed = true;
                    }
                }
            }
        }

        if (changed && commandAdapter != null) {
            commandAdapter.notifyDataSetChanged();
        }
    }

    private CommandItem findCommandByKey(String key) {
        for (CommandItem item : commandList) {
            if (item.getCommandKey().equals(key)) {
                return item;
            }
        }
        return null;
    }

    private void showCommandOptionsDialog(CommandItem item, int position) {
        List<CharSequence> optionsList = new ArrayList<>();

        if (!item.isDisabled()) {
            optionsList.add("Disable");
        } else {
            optionsList.add("Enable");
        }

        if (position > 11) {
            optionsList.add("Edit");
            optionsList.add("Copy");
            optionsList.add("Delete");
        }

        CharSequence[] options = optionsList.toArray(new CharSequence[0]);

        new AlertDialog.Builder(this)
                .setTitle(item.getLabel())
                .setItems(options, (d, which) -> {
                    String selectedOption = (String) options[which];

                    if (selectedOption.equals("Disable")) {
                        item.setDisabled(true);
                        saveDisabledCommands();
                        commandAdapter.notifyItemChanged(position);
                        Toast.makeText(this, item.getLabel() + " disabled", Toast.LENGTH_SHORT).show();
                    } else if (selectedOption.equals("Enable")) {
                        item.setDisabled(false);
                        saveDisabledCommands();
                        commandAdapter.notifyItemChanged(position);
                        Toast.makeText(this, item.getLabel() + " enabled", Toast.LENGTH_SHORT).show();
                    } else if (selectedOption.equals("Edit")) {
                        showEditCommandDialog(item, position);
                    } else if (selectedOption.equals("Copy")) {
                        CommandItem duplicate = new CommandItem(
                                item.getIcon(),
                                item.getLabel() + " Copy",
                                item.getDescription(),
                                item.getCommand(),
                                item.getCommandKey() + "_copy",
                                item.isToggleable(),
                                false
                        );
                        commandList.add(position + 1, duplicate);
                        commandAdapter.notifyItemInserted(position + 1);
                        saveCommandStates();
                    } else if (selectedOption.equals("Delete")) {
                        commandList.remove(position);
                        commandAdapter.notifyItemRemoved(position);
                        saveCommandStates();
                    }
                })
                .show();
    }

    private void showEditCommandDialog(CommandItem item, int position) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_command, null);
        EditText iconInput = view.findViewById(R.id.iconInput);
        EditText labelInput = view.findViewById(R.id.labelInput);
        EditText descInput = view.findViewById(R.id.descInput);
        EditText commandInput = view.findViewById(R.id.commandInput);

        iconInput.setText(item.getIcon());
        labelInput.setText(item.getLabel());
        descInput.setText(item.getDescription());
        commandInput.setText(item.getCommand());

        new AlertDialog.Builder(this)
                .setTitle("Edit Command")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {
                    commandAdapter.notifyItemChanged(position);
                    saveCommandStates();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAlarmDialog(CommandItem item) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_alarm, null);
        TimePicker timePicker = view.findViewById(R.id.timePicker);
        EditText durationInput = view.findViewById(R.id.durationInput);
        Button btnPlus = view.findViewById(R.id.btnPlus);
        Button btnMinus = view.findViewById(R.id.btnMinus);

        timePicker.setIs24HourView(true);
        durationInput.setText("10");

        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable updater = new Runnable() {
            @Override
            public void run() {
                int current = Integer.parseInt(durationInput.getText().toString().isEmpty() ? "0" : durationInput.getText().toString());
                if (btnPlus.isPressed()) {
                    durationInput.setText(String.valueOf(current + 1));
                    handler.postDelayed(this, 100);
                } else if (btnMinus.isPressed() && current > 0) {
                    durationInput.setText(String.valueOf(current - 1));
                    handler.postDelayed(this, 100);
                }
            }
        };

        btnPlus.setOnClickListener(v -> {
            int val = Integer.parseInt(durationInput.getText().toString());
            durationInput.setText(String.valueOf(val + 1));
        });

        btnPlus.setOnLongClickListener(v -> {
            handler.post(updater);
            return true;
        });

        btnMinus.setOnClickListener(v -> {
            int val = Integer.parseInt(durationInput.getText().toString());
            if (val > 0) durationInput.setText(String.valueOf(val - 1));
        });

        btnMinus.setOnLongClickListener(v -> {
            handler.post(updater);
            return true;
        });

        new AlertDialog.Builder(this)
                .setTitle("Set " + item.getLabel())
                .setView(view)
                .setPositiveButton("Send", (d, w) -> {
                    int hour, minute;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        hour = timePicker.getHour();
                        minute = timePicker.getMinute();
                    } else {
                        hour = timePicker.getCurrentHour();
                        minute = timePicker.getCurrentMinute();
                    }

                    String time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                    String duration = durationInput.getText().toString().trim();

                    if (duration.isEmpty()) duration = "0";

                    String cmd = item.getCommandBase() + " " + time + "," + duration;
                    sendCommand(cmd, item, commandList.indexOf(item));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendCommand(String cmdText, CommandItem item, int position) {
        String phone = phoneNumberInput.getText().toString().trim();
        if (phone.isEmpty()) {
            Toast.makeText(this, "Please configure phone number in settings", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            SmsManager.getDefault().sendTextMessage(phone, null, cmdText, null, null);
            addMessageToList("SENT", cmdText);

            if (position >= 0 && position < commandList.size()) {
                item.setLoading(true);
                commandAdapter.notifyItemChanged(position);
                pendingCommand = item;
            }

            int timeout = prefs.getInt("timeout", 30) * 1000;
            timeoutHandler.postDelayed(() -> {
                if (item != null && item.isLoading()) {
                    item.setLoading(false);
                    if (position >= 0 && position < commandList.size()) {
                        commandAdapter.notifyItemChanged(position);
                    }
                    addMessageToList("RECEIVED", "No response received");
                    failedAttempts++;
                    updateStatusUI(phone);
                }
            }, timeout);
        } catch (Exception e) {
            Toast.makeText(this, "SMS Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            failedAttempts++;
        }
    }

    private void handleDeviceResponse(String response) {
        lastResponseTime = System.currentTimeMillis();
        failedAttempts = 0;

        checkForSensorAlerts(response);

        if (prefs.getBoolean("vibrate", true)) {
            vibrate();
        }

        if (prefs.getBoolean("sound", false)) {
            playNotificationSound();
        }

        addMessageToList("RECEIVED", response);
        timeoutHandler.removeCallbacksAndMessages(null);
        updateStatusUI(phoneNumberInput.getText().toString().trim());

        if (pendingCommand != null) {
            pendingCommand.setLoading(false);
            pendingCommand = null;
        }

        parseAndUpdateDeviceStatus(response);
        commandAdapter.notifyDataSetChanged();
        updateStatusDisplay();
    }

    private void checkForSensorAlerts(String response) {
        String resLower = response.toLowerCase();

        if (resLower.contains("door sensor enabled") || resLower.contains("door sensor")) {
            showSensorAlert("🚪 Door Sensor", "Door sensor is active");
        }

        if (resLower.contains("pir motion detected")) {
            String motionType = resLower.contains("long motion") ? "Long motion detected" :
                    (resLower.contains("double trigger") ? "Double trigger - Alert!" : "Motion detected");
            showSensorAlert("🏃 PIR Motion Sensor", motionType);
            turnOnScreenAndAlert();
        }

        if (resLower.contains("laser detects") || resLower.contains("laser detected")) {
            showSensorAlert("🔴 Laser Sensor", "Laser activated - Alert!");
            turnOnScreenAndAlert();
        }
    }

    private void showSensorAlert(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setCategory(NotificationCompat.CATEGORY_ALARM);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void turnOnScreenAndAlert() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 500}, -1));
            } else {
                vibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, -1);
            }
        }

        try {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            Ringtone ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
            ringtone.play();

            new Handler(Looper.getMainLooper()).postDelayed(ringtone::stop, 3000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseAndUpdateDeviceStatus(String response) {
        try {
            String responseLower = response.toLowerCase();

            if (responseLower.contains("sensors:") || responseLower.contains("alarms:") || responseLower.contains(">l")) {
                parseStatusResponse(response);
            }

            if (responseLower.contains("turned on") || responseLower.contains("turned off")) {
                parseDeviceToggleResponse(response);
            }

            if (responseLower.contains("alarm") && (responseLower.contains("set") || responseLower.contains("is on") || responseLower.contains("is off"))) {
                parseAlarmResponse(response);
            }

            if (responseLower.contains("door sensor enabled")) {
                updateCommandState("sensors", "ON");
            } else if (responseLower.contains("door sensor disabled")) {
                updateCommandState("sensors", "OFF");
            }

            if (responseLower.contains("alarms enabled") || (responseLower.contains("alarms") && responseLower.contains("turned on"))) {
                updateCommandState("alarms", "ON");
            } else if (responseLower.contains("alarms disabled") || (responseLower.contains("alarms") && responseLower.contains("turned off"))) {
                updateCommandState("alarms", "OFF");
            }

            saveCommandStates();
            commandAdapter.notifyDataSetChanged();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseStatusResponse(String response) {
        String[] lines = response.split("\n");

        if (lines.length > 0 && lines[0].contains(":")) {
            String rawBoardTime = lines[0].trim();
            runOnUiThread(() -> boardTimeText.setText("Board Time: " + rawBoardTime));
        }

        for (String line : lines) {
            line = line.trim();

            if (line.contains(":")) {
                String[] parts = line.split(":");
                if (parts.length >= 2) {
                    String deviceName = parts[0].trim().replace(">", "");
                    String statusPart = parts[1].trim();
                    String[] statusTokens = statusPart.split(" ");
                    String status = statusTokens[0];

                    updateCommandState(deviceName, status);

                    Pattern timePattern = Pattern.compile("A(\\d{2}:\\d{2})");
                    Pattern durationPattern = Pattern.compile("D(\\d+)");

                    Matcher timeMatcher = timePattern.matcher(line);
                    Matcher durationMatcher = durationPattern.matcher(line);

                    if (timeMatcher.find() && durationMatcher.find()) {
                        String time = timeMatcher.group(1);
                        String duration = durationMatcher.group(1);
                        String key = deviceName.toLowerCase();
                        alarmInfo.put(key, "at " + time + " for " + duration + "min");
                        updateCommandDescription(deviceName, "⏰ " + time + " • " + duration + "min");
                    }
                }
            }
        }
    }

    private void parseDeviceToggleResponse(String response) {
        String responseLower = response.toLowerCase();
        Pattern pattern = Pattern.compile("(led\\d+|water|fan)\\s+turned\\s+(on|off)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(responseLower);

        if (matcher.find()) {
            String device = matcher.group(1);
            String state = matcher.group(2);
            updateCommandState(device, state);
            updateCommandDescription(device, (state.equalsIgnoreCase("on") ? "Turned ON" : "Turned OFF") + " via SMS");
        }
    }

    private void parseAlarmResponse(String response) {
        String responseLower = response.toLowerCase();
        Pattern setPattern = Pattern.compile("alarm\\s+(water\\d+|led\\d+)\\s+set\\s+(\\d{2}:\\d{2}),\\s+dur\\s+(\\d+)\\s+mins", Pattern.CASE_INSENSITIVE);
        Matcher setMatcher = setPattern.matcher(responseLower);

        if (setMatcher.find()) {
            String device = setMatcher.group(1);
            String time = setMatcher.group(2);
            String duration = setMatcher.group(3);
            String commandKey = mapAlarmToCommand(device);
            if (commandKey != null) {
                alarmInfo.put(commandKey, "at " + time + " for " + duration + "min");
                updateCommandDescription(commandKey, "⏰ " + time + " • " + duration + "min");
            }
        }

        Pattern activeMatcherPattern = Pattern.compile("(water|led\\d+)\\s+is\\s+(on|off)", Pattern.CASE_INSENSITIVE);
        Matcher activeMatcher = activeMatcherPattern.matcher(responseLower);

        if (activeMatcher.find()) {
            String device = activeMatcher.group(1);
            String state = activeMatcher.group(2);
            updateCommandState(device, state);
            Pattern datePattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})\\s+(\\w+)\\s+(\\d{2}:\\d{2})");
            Matcher dateMatcher = datePattern.matcher(response);
            if (dateMatcher.find()) {
                updateCommandDescription(device, (state.equalsIgnoreCase("on") ? "🟢 ON at " : "🔴 OFF at ") + dateMatcher.group(3));
            }
        }
    }

    private String mapAlarmToCommand(String alarmDevice) {
        String lower = alarmDevice.toLowerCase();
        if (lower.contains("water")) return "l1";
        if (lower.contains("led2")) return "l2";
        if (lower.contains("led3")) return "l3";
        return null;
    }

    private void updateCommandState(String deviceName, String status) {
        boolean isOn = status.equalsIgnoreCase("ON");
        String deviceKey = deviceName.toLowerCase();
        deviceStates.put(deviceKey, isOn);

        if (deviceKey.equals("alarms")) updateDependentButtons(isOn);

        Map<String, String> deviceMapping = new HashMap<>();
        deviceMapping.put("sensors", "sensors"); deviceMapping.put("alarms", "alarms");
        deviceMapping.put("l1", "l1"); deviceMapping.put("led1", "l1");
        deviceMapping.put("l2", "l2"); deviceMapping.put("led2", "l2");
        deviceMapping.put("l3", "l3"); deviceMapping.put("led3", "l3");
        deviceMapping.put("water", "water"); deviceMapping.put("fan", "fan");

        for (CommandItem item : commandList) {
            String itemKey = item.getCommandKey().toLowerCase();
            if (itemKey.equals(deviceKey) || (deviceMapping.containsKey(deviceKey) && deviceMapping.get(deviceKey).equals(itemKey))) {
                if (item.isToggleable()) item.setCurrentState(isOn);
                break;
            }
        }
    }

    private void updateCommandDescription(String deviceName, String description) {
        String deviceKey = deviceName.toLowerCase();
        for (CommandItem item : commandList) {
            if (item.getCommandKey().toLowerCase().contains(deviceKey)) {
                item.setDescription(description);
                break;
            }
        }
    }

    private void saveCommandStates() {
        Map<String, Boolean> states = new HashMap<>();
        for (CommandItem item : commandList) {
            if (item.isToggleable()) states.put(item.getCommandKey(), item.getCurrentState());
        }
        prefs.edit().putString("command_states", new Gson().toJson(states)).apply();
    }

    private void loadCommandStates() {
        String json = prefs.getString("command_states", null);
        if (json != null) {
            Type type = new TypeToken<HashMap<String, Boolean>>() {}.getType();
            Map<String, Boolean> states = new Gson().fromJson(json, type);
            if (states != null) {
                for (CommandItem item : commandList) {
                    if (states.containsKey(item.getCommandKey())) {
                        item.setCurrentState(states.get(item.getCommandKey()));
                        deviceStates.put(item.getCommandKey(), states.get(item.getCommandKey()));
                        if (item.getCommandKey().equals("alarms")) updateDependentButtons(states.get(item.getCommandKey()));
                    }
                }
            }
        }
    }

    private void saveDisabledCommands() {
        List<String> disabledKeys = new ArrayList<>();
        for (CommandItem item : commandList) {
            if (item.isDisabled()) disabledKeys.add(item.getCommandKey());
        }
        prefs.edit().putString("disabled_commands", new Gson().toJson(disabledKeys)).apply();
    }

    private void loadDisabledCommands() {
        String json = prefs.getString("disabled_commands", null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<String>>() {}.getType();
            List<String> disabledKeys = new Gson().fromJson(json, type);
            if (disabledKeys != null) {
                for (CommandItem item : commandList) {
                    if (disabledKeys.contains(item.getCommandKey())) item.setDisabled(true);
                }
            }
        }
    }

    private void vibrate() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(100);
                }
            }
        }
    }

    private void playNotificationSound() {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            RingtoneManager.getRingtone(getApplicationContext(), notification).play();
        } catch (Exception ignored) {}
    }

    private void addMessageToList(String type, String content) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        messageList.add(0, new MessageItem(type, content, time));
        if (messageList.size() > 100) messageList.remove(messageList.size() - 1);
        messageAdapter.notifyItemInserted(0);
        messagesRecyclerView.scrollToPosition(0);
        saveChatHistory();
    }

    private void saveChatHistory() {
        prefs.edit().putString("chat_history", new Gson().toJson(messageList)).apply();
    }

    private void loadChatHistory() {
        String json = prefs.getString("chat_history", null);
        if (json != null) {
            Type type = new TypeToken<ArrayList<MessageItem>>() {}.getType();
            List<MessageItem> saved = new Gson().fromJson(json, type);
            if (saved != null) messageList.addAll(saved);
        }
    }

    private void checkPermissions() {
        String[] permissions = {Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.VIBRATE, Manifest.permission.POST_NOTIFICATIONS};
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) listPermissionsNeeded.add(p);
        }
        if (!listPermissionsNeeded.isEmpty()) ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        liveClockHandler.post(liveClockRunnable);
        if (autoRefreshEnabled) startAutoRefresh();
        updateStatusDisplay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        liveClockHandler.removeCallbacks(liveClockRunnable);
        stopAutoRefresh();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(smsReceiver); } catch (Exception ignored) {}
        timeoutHandler.removeCallbacksAndMessages(null);
        stopAutoRefresh();
    }
}