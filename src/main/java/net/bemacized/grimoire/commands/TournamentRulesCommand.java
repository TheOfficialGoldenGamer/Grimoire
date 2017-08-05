package net.bemacized.grimoire.commands;

import net.bemacized.grimoire.Grimoire;
import net.bemacized.grimoire.model.models.TournamentRule;
import net.bemacized.grimoire.utils.MessageUtils;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.stream.Collectors;

public class TournamentRulesCommand extends BaseCommand {
	@Override
	public String name() {
		return "tournamentrules";
	}

	@Override
	public String[] aliases() {
		return new String[]{"magictournamentrules", "mtr", "tr"};
	}

	@Override
	public String description() {
		return "Retrieve a paragraph from the tournament rules";
	}

	@Override
	public String paramUsage() {
		return "<paragraph nr>";
	}

	@Override
	public void exec(String[] args, MessageReceivedEvent e) {
		// Verify that paragraph number was given
		if (args.length == 0) {
			sendEmbedFormat(e.getChannel(), "Please specify a chapter (ex. `1` or `1.1`). The following sections are available:\n%s", String.join("\n",
					Grimoire.getInstance().getTournamentRules().getRules().parallelStream()
							.map(section -> ":small_orange_diamond: **" + section.getParagraphNr() + "** " + section.getTitle())
							.collect(Collectors.toList())
			));
			return;
		}

		// Handle subsections
		if (args[0].matches("[0-9]+[.][0-9]+")) {
			// Find section
			TournamentRule section = Grimoire.getInstance().getTournamentRules().getRules().parallelStream().filter(s -> args[0].startsWith(s.getParagraphNr())).findFirst().orElse(null);
			if (section == null) {
				sendEmbedFormat(e.getChannel(), "The section you specified is unknown. Please choose one of the following:\n%s",
						String.join("\n", Grimoire.getInstance().getTournamentRules().getRules().parallelStream()
								.map(s -> ":small_orange_diamond: **" + s.getParagraphNr() + "** " + s.getTitle())
								.collect(Collectors.toList()))
				);
				return;
			}
			// Find subsection
			TournamentRule.SubSection subsection = section.getSubsections().parallelStream().filter(s -> s.getParagraphNr().equalsIgnoreCase(args[0])).findFirst().orElse(null);
			if (subsection == null) {
				sendEmbedFormat(e.getChannel(), "The subsection you specified is unknown. \nThe following subsections are available in **%s %s**:\n%s", section.getParagraphNr(), section.getTitle(),
						String.join("\n", section.getSubsections().parallelStream().map(s -> ":small_orange_diamond: **" + s.getParagraphNr() + "** " + s.getTitle()).collect(Collectors.toList()))
				);
				return;
			}
			// Show text
			String[] splits = MessageUtils.splitMessage(subsection.getContent());
			for (int i = 0; i < splits.length; i++) {
				EmbedBuilder eb = new EmbedBuilder().setDescription(splits[i]);
				if (i == 0)
					eb = eb.setAuthor("Magic Tournament Rules", null, null).setTitle(String.format("%s %s: %s", subsection.getParagraphNr(), section.getTitle(), subsection.getTitle()));
				e.getChannel().sendMessage(eb.build()).submit();
			}

		}
		// Handle sections
		else {
			// Find section
			TournamentRule section = Grimoire.getInstance().getTournamentRules().getRules().parallelStream().filter(s -> s.getParagraphNr().startsWith(args[0])).findFirst().orElse(null);
			// Check if section was found
			if (section == null) {
				sendEmbedFormat(e.getChannel(), "The section you specified is unknown. Please choose one of the following:\n",
						String.join("\n", Grimoire.getInstance().getTournamentRules().getRules().parallelStream().map(s -> ":small_orange_diamond: **" + s.getParagraphNr() + "** " + s.getTitle()).collect(Collectors.toList()))
				);
				return;
			}
			String[] splits = MessageUtils.splitMessage("The following subsections are available:\n" + String.join("\n", section.getSubsections().parallelStream().map(s -> ":small_orange_diamond: **" + s.getParagraphNr() + "** " + s.getTitle()).collect(Collectors.toList())));
			for (int i = 0; i < splits.length; i++) {
				EmbedBuilder eb = new EmbedBuilder().setDescription(splits[i]);
				if (i == 0)
					eb = eb.setAuthor("Magic Tournament Rules", null, null).setTitle(String.format("%s %s", section.getParagraphNr(), section.getTitle()));
				e.getChannel().sendMessage(eb.build()).submit();
			}
		}
	}
}
