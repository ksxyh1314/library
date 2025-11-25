// --- com.library.ui.LossResolutionDialog.java (新文件) ---
package com.library.ui;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;

public class LossResolutionDialog extends JDialog {
    private JRadioButton rbFine;
    private JRadioButton rbReplacement;
    private JTextField txtAmount;
    private boolean confirmed = false;
    private String resolutionType = null;
    private double amount = 0.0;
    public LossResolutionDialog(Window owner, int bookId) {
        super(owner, "处理图书遗失 ID: " + bookId, ModalityType.APPLICATION_MODAL);
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    // 格式化金额，确保输入的是两位小数
    private static final DecimalFormat df = new DecimalFormat("0.00");

    public LossResolutionDialog(Frame owner, int bookId) {
        super(owner, "处理图书遗失 ID: " + bookId, true);
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        JPanel mainPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // --- 选项面板 ---
        JPanel optionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPanel.setBorder(BorderFactory.createTitledBorder("选择处理方式"));

        rbFine = new JRadioButton("罚款");
        rbReplacement = new JRadioButton("新书替换 (原价)");
        ButtonGroup group = new ButtonGroup();
        group.add(rbFine);
        group.add(rbReplacement);

        rbFine.setSelected(true); // 默认选中罚款
        optionPanel.add(rbFine);
        optionPanel.add(rbReplacement);
        mainPanel.add(optionPanel);

        // --- 罚款金额面板 ---
        JPanel finePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        finePanel.setBorder(BorderFactory.createTitledBorder("罚款金额 (若选择罚款)"));
        txtAmount = new JTextField(df.format(0.00), 10);
        finePanel.add(new JLabel("金额 (元):"));
        finePanel.add(txtAmount);
        mainPanel.add(finePanel);

        // 监听器：根据选项启用/禁用金额输入
        rbFine.addActionListener(e -> txtAmount.setEnabled(true));
        rbReplacement.addActionListener(e -> txtAmount.setEnabled(false));


        // --- 按钮面板 ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnConfirm = new JButton("确认处理");
        JButton btnCancel = new JButton("取消");
        buttonPanel.add(btnConfirm);
        buttonPanel.add(btnCancel);

        btnConfirm.addActionListener(e -> onConfirm());
        btnCancel.addActionListener(e -> dispose());

        add(mainPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onConfirm() {
        if (rbReplacement.isSelected()) {
            resolutionType = "Replacement";
            amount = 0.0;
            confirmed = true;
            dispose();
            return;
        }

        // 罚款处理
        resolutionType = "Fine";
        try {
            // 严格的数字解析和校验
            amount = Double.parseDouble(txtAmount.getText().trim());
            if (amount <= 0) {
                JOptionPane.showMessageDialog(this, "罚款金额必须大于 0。", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // 确保只保留两位小数
            amount = Math.floor(amount * 100) / 100;

            confirmed = true;
            dispose();

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的金额。", "输入错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getResolutionType() {
        return resolutionType;
    }

    public double getAmount() {
        return amount;
    }
}