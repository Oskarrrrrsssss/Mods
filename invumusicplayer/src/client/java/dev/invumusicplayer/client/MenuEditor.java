package dev.invumusicplayer.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class MenuEditor extends Screen {
    private final Screen parent;private boolean dragging;private double dx,dy;
    public MenuEditor(Screen parent){super(Component.translatable("invumusicplayer.menu.position"));this.parent=parent;}
    private MenuPlayerPanel.Layout layout(){return MenuPlayerPanel.layout(width,height,false);}
    @Override protected void init(){int x=width/2-155,y=height-56;
        button(x,y,100,"invumusicplayer.hud_editor.scale_down",()->resize(-.05f));button(x+105,y,100,"invumusicplayer.hud_editor.scale_up",()->resize(.05f));
        button(x+210,y,100,"invumusicplayer.menu.reset",()->{var s=InvuMusicPlayerClient.player.settings;s.menuPositioned=false;s.menuScale=.5f;save();});
        String[] corners={"top_left","top_right","bottom_left","bottom_right","done"};
        for(int i=0;i<corners.length;i++){int at=i;button(x+i*63,height-30,58,"invumusicplayer.hud_editor."+corners[i],()->{if(at==4)onClose();else corner(at%2==1,at>=2);});}
    }
    private void button(int x,int y,int w,String key,Runnable action){var b=new ModernButton(x,y,w,20,Component.translatable(key),v->action.run());b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(key)));addRenderableWidget(b);}
    private void save(){InvuMusicPlayerClient.player.saveSettings();}
    private void resize(float amount){var s=InvuMusicPlayerClient.player.settings;s.menuScale+=amount;s.normalize();save();}
    private void corner(boolean right,boolean bottom){var s=InvuMusicPlayerClient.player.settings;s.menuPositioned=true;s.menuX=right?1:0;s.menuY=bottom?1:0;save();}
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){if(super.mouseClicked(mouseX,mouseY,button))return true;var l=layout();if(mouseX>=l.x()&&mouseX<=l.x()+l.width()&&mouseY>=l.y()&&mouseY<=l.y()+l.height()){dragging=true;dx=mouseX-l.x();dy=mouseY-l.y();return true;}return false;}
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double x,double y){if(!dragging)return super.mouseDragged(mouseX,mouseY,button,x,y);var l=layout();var s=InvuMusicPlayerClient.player.settings;s.menuPositioned=true;s.menuX=(float)((mouseX-dx-8)/Math.max(1,width-l.width()-16));s.menuY=(float)((mouseY-dy-8)/Math.max(1,height-l.height()-16));s.normalize();return true;}
    @Override public boolean mouseReleased(double mouseX,double mouseY,int button){if(dragging){dragging=false;save();return true;}return super.mouseReleased(mouseX,mouseY,button);}
    @Override public void render(GuiGraphics g,int mx,int my,float d){g.fill(0,0,width,height,0xb00c1220);var l=layout();int tx=l.x()<width/2?l.x()+l.width()+12:8;g.drawString(font,font.plainSubstrByWidth(title.getString(),width-tx-8),tx,12,0xffedf2fb,false);g.drawString(font,font.plainSubstrByWidth(UiText.text("invumusicplayer.menu.drag_hint"),width-tx-8),tx,26,0xffbdc6d5,false);MenuPlayerPanel.draw(g,l);g.fill(l.x(),l.controlY(228),l.x()+l.width(),l.atY(MenuPlayerPanel.baseHeight()),0x7035425c);g.drawString(font,UiText.text("invumusicplayer.hud_editor.scale",Math.round(InvuMusicPlayerClient.player.settings.menuScale*100)),width/2-155,height-70,0xffbdc6d5,false);super.render(g,mx,my,d);}
    @Override public void onClose(){dragging=false;save();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return parent!=null&&parent.isPauseScreen();}
}
