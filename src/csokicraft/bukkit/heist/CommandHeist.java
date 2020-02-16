package csokicraft.bukkit.heist;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandHeist extends Command{

	protected CommandHeist(String name){
		super(name);
	}

	@Override
	public boolean execute(CommandSender arg0, String arg1, String[] arg2){
		if(arg2.length<1)
			return false;
		switch (arg2[0]){
		case "help":
			arg0.sendMessage(SimpleHeist.getInstance().__("desc_compass"));
			arg0.sendMessage(SimpleHeist.getInstance().__("desc_stop"));
		case "compass":
			if(arg0 instanceof Player)
				SimpleHeist.getInstance().giveCompass((Player) arg0);
			else
				arg0.sendMessage(SimpleHeist.getInstance().__("err_not_player"));
			return true;
		case "stop":
			if(!arg0.hasPermission("simpleheist.admin")){
				arg0.sendMessage(SimpleHeist.getInstance().__("err_not_admin"));
				return true;
			}
			SimpleHeist.getInstance().endHeist();
		default:
			return false;
		}
	}
}
