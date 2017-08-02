package net.bemacized.grimoire.utils;

import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.MessageEmbed;

import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LoadMessage {

	private final static Logger LOG = Logger.getLogger(LoadMessage.class.getName());
	private final static String SPINNER = "⣾⣽⣻⢿⡿⣟⣯⣷";
	private final static long EXPIRE_TIME = 1000 * 90;
	private final static int SPINNER_INTERVAL = 1500;

	private List<Message> messages;
	private List<String> lines;
	private int spinnerStage;
	private RunnableQueue taskQueue;
	private boolean finished;
	private Timer spinnerTimer;
	private boolean showSpinner;
	private long startTime;
	private MessageChannel channel;

	public LoadMessage(MessageChannel channel, String msg, boolean showSpinner) {
		// Initialize fields
		this.showSpinner = showSpinner;
		this.lines = new ArrayList<>();
		this.messages = new ArrayList<>();
		this.finished = false;
		this.spinnerStage = new Random().nextInt(SPINNER.length());
		this.taskQueue = new RunnableQueue(SPINNER_INTERVAL);
		this.spinnerTimer = new Timer();
		this.startTime = System.currentTimeMillis();
		this.channel = channel;

		// Add line(s) to list
		this.lines.addAll(Arrays.stream(msg.split("[\n\r]")).collect(Collectors.toList()));

		// Start timer for spinner & autofinish
		this.spinnerTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (System.currentTimeMillis() - startTime >= EXPIRE_TIME) LoadMessage.this.finish();
				spinnerStage++;
				if (spinnerStage == SPINNER.length()) spinnerStage = 0;
				if (taskQueue.isEmpty()) taskQueue.queue(LoadMessage.this::render);
			}
		}, 0, SPINNER_INTERVAL);
	}

	public void setLineFormat(String template, Object... objs) {
		setLine(String.format(template, objs));
	}

	public LoadMessage setLine(String line) {
		this.lines.clear();
		return this.addLine(line);
	}

	public void addLineFormat(String template, Object... objs) {
		addLine(String.format(template, objs));
	}

	public LoadMessage addLine(String line) {
		this.lines.addAll(Arrays.stream(line.split("[\n\r]")).collect(Collectors.toList()));
		taskQueue.queue(this::render);
		return this;
	}

	public LoadMessage showSpinner(boolean enabled) {
		this.showSpinner = enabled;
		if (!enabled) spinnerStage = 0;
		return this;
	}

	private void render() {
		try {
			final StringBuilder sb = new StringBuilder();
			if (showSpinner) sb.append(SPINNER.toCharArray()[spinnerStage]).append(" ");
			sb.append(String.join("\n", this.lines));
			String[] messageTexts = StringUtils.splitMessage(sb.toString().trim(), true);
			for (int i = 0; i < messageTexts.length; i++) {
				// Delete messages that exceed amount of messages required
				for (int j = messages.size() - 1; j >= messageTexts.length; j--) {
					messages.get(j).delete().submit();
					messages.remove(j);
				}

				// Update old messages if they exist, otherwise send new ones.
				String newText = messageTexts[i];
				Message oldMsg = (messages.size() > i) ? messages.get(i) : null;
				if (oldMsg == null) {
					oldMsg = channel.sendMessage(newText).submit().get();
					messages.add(oldMsg);
				} else if (!oldMsg.getContent().equals(newText)) {
					oldMsg = oldMsg.editMessage(newText).submit().get();
					messages.remove(i);
					messages.add(i, oldMsg);
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			LOG.log(Level.SEVERE, "Could not render LoadMessage", e);
		}
	}

	private void finish() {
		this.finished = true;
		spinnerTimer.cancel();
	}

	public void finalize() {
		if (this.finished) throw new FinishedException();
		taskQueue.queue(() -> {
			messages.forEach(msg -> msg.delete().submit());
			messages.clear();
			finish();
		});
	}

	public void finalizeFormat(String template, Object... objs) {
		finalize(String.format(template, objs));
	}

	public void finalize(String msg) {
		this.finalize((Object) msg);
	}

	public void finalize(Message msg) {
		this.finalize((Object) msg);
	}

	public void finalize(MessageEmbed msg) {
		this.finalize((Object) msg);
	}

	private void finalize(Object msg) {
		if (this.finished) throw new FinishedException();
		taskQueue.queue(() -> {
			for (int i = 0; i < messages.size(); i++) {
				if (i < messages.size() - 1)
					try {
						messages.get(i).delete().submit();
					} catch (Exception e) {
						LOG.log(Level.SEVERE, "Could not remove message object", e);
					}
				else if (msg instanceof String)
					try {
						messages.get(i).editMessage((String) msg).submit();
					} catch (Exception e) {
						messages.get(i).getChannel().sendMessage((String) msg).submit();
					}
				else if (msg instanceof Message)
					try {
						messages.get(i).editMessage((Message) msg).submit();
					} catch (Exception e) {
						messages.get(i).getChannel().sendMessage((Message) msg).submit();
					}
				else if (msg instanceof MessageEmbed)
					try {
						messages.get(i).editMessage((MessageEmbed) msg).submit();
					} catch (Exception e) {
						messages.get(i).getChannel().sendMessage((MessageEmbed) msg).submit();
					}
				else
					throw new InvalidParameterException("msg parameter must be a String, Message, or MessageEmbed object");
			}
			finish();
		});
	}

	private class FinishedException extends RuntimeException {
		FinishedException() {
			this("This object has already exceeded its purpose.");
		}

		private FinishedException(String message) {
			super(message);
		}
	}

}