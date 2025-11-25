package com.library.ui;

import javax.swing.*;
import java.awt.*;

/**
 * 通用的图书信息输入对话框，适用于新增和修改。
 */
public class BookInputDialog extends JDialog {
    private JTextField titleField;
    private JTextField authorField;
    private boolean confirmed = false;

    /**
     * @param parent 父窗口
     * @param dialogTitle 对话框标题 ("新增图书" 或 "修改图书信息")
     * @param initialTitle 初始书名 (新增时传入空字符串或null，修改时传入旧书名)
     * @param initialAuthor 初始作者 (新增时传入空字符串或null，修改时传入旧作者)
     */
    public BookInputDialog(Frame parent, String dialogTitle, String initialTitle, String initialAuthor) {
        super(parent, dialogTitle, true); // Modal dialog

        setLayout(new BorderLayout(10, 10));

        // 确保初始值为非null
        String title = initialTitle == null ? "" : initialTitle;
        String author = initialAuthor == null ? "佚名" : initialAuthor;

        // 表单面板
        JPanel formPanel = new JPanel(new GridLayout(2, 2, 10, 10));

        titleField = new JTextField(title, 20);
        authorField = new JTextField(author, 20);

        formPanel.add(new JLabel("书名:"));
        formPanel.add(titleField);
        formPanel.add(new JLabel("作者:"));
        formPanel.add(authorField);

        // 外部容器，增加边距
        JPanel contentPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.add(formPanel);

        add(contentPanel, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel();
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");

        okButton.addActionListener(e -> {
            if (titleField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "书名不能为空。", "输入错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            confirmed = true;
            setVisible(false);
            dispose();
        });

        cancelButton.addActionListener(e -> {
            setVisible(false);
            dispose();
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public String getNewTitle() {
        return titleField.getText().trim();
    }

    public String getNewAuthor() {
        // 如果作者为空，可以提供默认值
        String author = authorField.getText().trim();
        return author.isEmpty() ? "佚名" : author;
    }

}