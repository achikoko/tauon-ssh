package tauon.app.ui.components.simpletable;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import tauon.app.App;

import javax.swing.*;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.util.*;
import java.util.function.Function;

public class SimpleTable<T> extends JTable {
    
    private final DefaultListModel<T> entities = new DefaultListModel<>();
    private final MyTableColumnModel myTableColumnModel;
    
    public SimpleTable(Builder<T> builder){
        setModel(myTableColumnModel = new MyTableColumnModel(builder.columns));
        
        setFillsViewportHeight(true);
//        setShowGrid(false);
//        setIntercellSpacing(new Dimension(0, 0));
        setAutoCreateRowSorter(true);
        
        Set<Class<?>> types = new HashSet<>();
        for(SimpleColumn<T, ?> c: builder.columns){
            types.add(c.type);
        }
        if(types.isEmpty())
            throw new RuntimeException("No columns where defined.");
        
        Integer maxColumnHeight = null;
        for (Class<?> c: types){
            TableCellRenderer renderer;
            if(c == ByteCountRenderer.class){
                setDefaultRenderer(ByteCountValue.class, renderer = new ByteCountRenderer());
            }else if(c == PercentageValue.class){
                setDefaultRenderer(PercentageValue.class, renderer = new PercentageRenderer());
            }else if(c == ConnectionStatusValue.class){
                setDefaultRenderer(ConnectionStatusValue.class, renderer = new ConnectionStatusRenderer());
            }else if(c == Object.class){
//                setDefaultRenderer(Object.class, renderer = new DefaultTableCellRenderer());
                continue;
            }else{
                throw new RuntimeException("Renderer for " + c + " is not defined.");
            }
            
            maxColumnHeight = Math.max(maxColumnHeight != null ? maxColumnHeight : 0, ((JComponent) renderer).getPreferredSize().height);
        }
        
        setSelectionForeground(App.skin.getDefaultSelectionForeground());
        
        if(maxColumnHeight != null)
            setRowHeight(maxColumnHeight);
        
//        getColumnModel().getColumn(0).setPreferredWidth(200);
    }
    
    /** Calls {@code action} with the entity of a row double-clicked with the left button. */
    public void onDoubleClick(java.util.function.Consumer<T> action) {
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() != 2 || !javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int viewRow = rowAtPoint(e.getPoint());
                if (viewRow >= 0) {
                    action.accept(entities.getElementAt(convertRowIndexToModel(viewRow)));
                }
            }
        });
    }

    public List<T> getEntitiesList() {
        return new EntitiesListDelegate(entities);
    }
    
    private class EntitiesListDelegate extends AbstractList<T>{
        
        DefaultListModel<T> delegated;
        
        public EntitiesListDelegate(DefaultListModel<T> delegated) {
            this.delegated = delegated;
        }
        
        @Override
        public int size() {
            return delegated.size();
        }
        
        @Override
        public boolean isEmpty() {
            return delegated.isEmpty();
        }
        
        @Override
        public boolean contains(Object o) {
            return delegated.contains(o);
        }
        
        @Override
        public boolean add(T t) {
            delegated.addElement(t);
            return true;
        }
        
        @Override
        public boolean remove(Object o) {
            return delegated.removeElement(o);
        }
        
        @Override
        public boolean addAll(@NotNull Collection<? extends T> c) {
            delegated.addAll(c);
            return true;
        }
        
        @Override
        public boolean addAll(int index, @NotNull Collection<? extends T> c) {
            delegated.addAll(index, c);
            return true;
        }
        
        @Override
        public void clear() {
            delegated.clear();
        }
        
        @Override
        public T get(int index) {
            return delegated.get(index);
        }
        
        @Override
        public T set(int index, T element) {
            return delegated.set(index, element);
        }
        
        @Override
        public void add(int index, T element) {
            delegated.add(index, element);
        }
        
        @Override
        public T remove(int index) {
            return delegated.remove(index);
        }
        
        @Override
        public int indexOf(Object o) {
            return delegated.indexOf(o);
        }
        
        @Override
        public int lastIndexOf(Object o) {
            return delegated.lastIndexOf(o);
        }
        
    }
    
    private class MyTableColumnModel extends AbstractTableModel {
        
        private final List<SimpleColumn<T, ?>> columns;
        
        public MyTableColumnModel(List<SimpleColumn<T, ?>> columns) {
            this.columns = columns;
        }
        
        @Override
        public int getRowCount() {
            return entities.getSize();
        }
        
        @Override
        public int getColumnCount() {
            return columns.size();
        }
        
        @Override
        public @Nls String getColumnName(int columnIndex) {
            return columns.get(columnIndex).name;
        }
        
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columns.get(columnIndex).type;
        }
        
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
        
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return columns.get(columnIndex).getValue.apply(entities.getElementAt(rowIndex));
        }
        
        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        
        }
        
        @Override
        public void addTableModelListener(TableModelListener l) {
        
        }
        
        @Override
        public void removeTableModelListener(TableModelListener l) {
        
        }
    }
    
    public static class Builder<T>{
        private final List<SimpleColumn<T, ?>> columns = new ArrayList<>();
        
        public SimpleTable.Builder<T> addColumn(SimpleColumn<T, ?> column){
            columns.add(column);
            return this;
        }
        
        public <V> SimpleTable.Builder<T> addColumn(String name, Class<V> type, Function<T, V> getValue) {
            addColumn(new SimpleColumn<T, V>(name, type, getValue));
            return this;
        }
        public <V> SimpleTable.Builder<T> addColumn(String name, Function<T, V> getValue) {
            addColumn(new SimpleColumn<T, V>(name, getValue));
            return this;
        }
        
        public SimpleTable<T> build(){
            return new SimpleTable<>(this);
        }
        
    }
    
}
