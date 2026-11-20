package tauon.app.ui.components.simpletable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

import static tauon.app.services.LanguageService.getBundle;

public class ConnectionStatusRenderer extends JLabel implements TableCellRenderer {

    public ConnectionStatusRenderer(){
        setText("ola");
        setBorder(new EmptyBorder(3, 3, 3, 3));
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus, int row, int column) {
        setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
        setFont(getFont().deriveFont(Font.BOLD));
        
        if(value == null){
            setText("");
        }else{
            switch ((ConnectionStatusValue)value){
                case DISABLED:
                    setText(getBundle().getString("value.connection_status.label.disabled"));
                    setForeground(Color.GRAY);
                    break;
                case ERROR:
                    setText(getBundle().getString("value.connection_status.label.error"));
                    setForeground(Color.RED);
                    break;
                case ESTABLISHED:
                    setText(getBundle().getString("value.connection_status.label.established"));
                    setForeground(Color.GREEN);
                    break;
                case STOPPED:
                    setText(getBundle().getString("value.connection_status.label.stopped"));
                    break;
                case RUNNING:
                    setText(getBundle().getString("value.connection_status.label.running"));
                    setForeground(Color.GREEN);
                    break;
                case INITIATING:
                    setText(getBundle().getString("value.connection_status.label.initiating"));
                    setForeground(Color.BLUE);
                    break;
            }
        }
        
        return this;
    }
}
