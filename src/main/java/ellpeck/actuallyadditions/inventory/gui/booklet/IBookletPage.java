/*
 * This file ("IBookletPage.java") is part of the Actually Additions Mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://github.com/Ellpeck/ActuallyAdditions/blob/master/README.md
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * � 2015 Ellpeck
 */

package ellpeck.actuallyadditions.inventory.gui.booklet;

import net.minecraft.item.ItemStack;

public interface IBookletPage{

    int getID();

    void setChapter(BookletChapter chapter);

    BookletChapter getChapter();

    String getText();

    void renderPre(GuiBooklet gui, int mouseX, int mouseY);

    void render(GuiBooklet gui, int mouseX, int mouseY);

    ItemStack getItemStackForPage();
}
