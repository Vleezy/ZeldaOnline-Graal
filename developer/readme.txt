This is the level editor for Zelda 3 Java version. 
Some notes:

1. To test your new levels you need the 
Zelda Online local version. Start that version with
"start.bat" (if you have the Java JDK) or "iestart.bat"
(if you have Internet Explorer). 

2.With this editor you can edit Zelda levels, additionally you
can add opponents, signs, and links. The predefined
Zelda levels are included in the Zelda Online local version
(*.zelda files).

3.You can select parts of the board with the mouse, move that
selections with the left mouse button and copy that selections
with the right mouse button. On the right side of the editor
window you can select predefined objects or single pieces and
move them onto the board on the left side of the window.

4. You can add new predefined objects: select a part of the
board, press the button left to the object choice box and type
in a name for the new object. You can also remove objects from
the list and save the list with the buttons right to the object choice
box.

5. Define opponents: Click on the opponents icon in the toolbar.
Every opponent gets an x/y coordinate (the board size is 64*64)
and a type.

6. Define links: These are fields that teleport the player to other
levels. Select an teleport area with the mouse and then click on the
arrow icon in the toolbar. Every link gets an x/y coordinate, width,
height, new x/y coordinate, and a filename. You can also define that
the x or y coordinate stays constant when the player is teleported.
Normally the automatic selected values are the right values.

7. Define signs: These are fields that show a special text when the
player goes onto it. You must define an x/y coordinate, width, height,
and the sign text. The text can contain multiple lines and some special
characters:
#A - a bold A
#B - a bold B
#X - a bold X
#Y - a bold Y
#u - an up arrow
#d - a down arrow
#l - a left arrow
#r - a right arrow
#h - the players head
#x - some cryptic text 1
#y - some cryptic text 1
#z - some cryptic text 1
#1 - 1/4 heart
#2 - 1/2 heart 
#3 - 3/4 heart
#4 - a full heart
#. - "..."

8. If you already played the Zelda Java version then you also
meet the stones that can be moved. If you want to put 
such a stone in your level, just put the PushStone
onto the board.

9. If you want me to implement your new created levels in the
official online version, then send them to me. You can also give me 
tips for the story.

10. When you close the editor window, then it automatically save
all window positions. When you start the editor a second time, then
all positions will be restored. Delete the file "ZeldaEditor.ini" if
you want to have the original window positions.


