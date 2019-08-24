/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package injectmoduleinfo;

/**
 *
 * @author draque
 */
public final class TextDisplayForm extends javax.swing.JFrame {

    /**
     * Creates new form TextDisplayForm
     * @param title
     * @param message
     */
    public TextDisplayForm(String title, String message) {
        initComponents();
        
        this.setTitle(title);
        txtDisplay.setText(message);
    }
    
    public static void run(String title, String message) {
        new TextDisplayForm(title, message).setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        txtDisplay = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        txtDisplay.setColumns(20);
        txtDisplay.setLineWrap(true);
        txtDisplay.setRows(5);
        txtDisplay.setWrapStyleWord(true);
        jScrollPane1.setViewportView(txtDisplay);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea txtDisplay;
    // End of variables declaration//GEN-END:variables
}
