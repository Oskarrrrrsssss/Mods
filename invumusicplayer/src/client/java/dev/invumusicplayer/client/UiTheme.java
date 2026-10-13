package dev.invumusicplayer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Album colors apply to listening surfaces; settings always keep their fixed palette. */
final class UiTheme {
    static boolean listening(Screen screen){return screen instanceof PlayerScreen||screen instanceof LibraryScreen||screen instanceof DeletePlaylistScreen;}
    static boolean fixed(Screen screen){return screen!=null&&!listening(screen)&&!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)&&!(screen instanceof net.minecraft.client.gui.screens.PauseScreen);}
    static int mix(int a,int b,float blend){int result=0;for(int shift:new int[]{16,8,0})result|=Math.round(((a>>shift)&255)*(1-blend)+((b>>shift)&255)*blend)<<shift;return result;}
    static int background(Screen screen){if(!listening(screen))return 0x0c1220;return mix(PlayerMenuButton.themeColor(),0x080e16,.45f);}
    static int control(){Screen screen=Minecraft.getInstance().gui.screen();return listening(screen)?background(screen):fixed(screen)?0x182237:PlayerMenuButton.themeColor();}
    static int accent(){return fixed(Minecraft.getInstance().gui.screen())?InvuMusicPlayerClient.player.settings.accent:PlayerMenuButton.themeAccent();}
    static int button(boolean highlighted){Screen screen=Minecraft.getInstance().gui.screen();return listening(screen)?mix(background(screen),highlighted?0xffffff:0x82909e,highlighted?.13f:.08f):highlighted?0x35425c:0x253149;}
}
