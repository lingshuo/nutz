package org.nutz.el.parse;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.nutz.el.ElException;
import org.nutz.el.Operator;
import org.nutz.el.Parse;
import org.nutz.el.obj.Elobj;
import org.nutz.el.obj.FieldObj;
import org.nutz.el.obj.IdentifierObj;
import org.nutz.el.obj.MethodObj;
import org.nutz.el.opt.arithmetic.LBracketOpt;
import org.nutz.el.opt.arithmetic.NegativeOpt;
import org.nutz.el.opt.arithmetic.RBracketOpt;
import org.nutz.el.opt.arithmetic.SubOpt;
import org.nutz.el.opt.object.AccessOpt;
import org.nutz.el.opt.object.CommaOpt;
import org.nutz.el.opt.object.InvokeMethodOpt;
import org.nutz.el.opt.object.MethodOpt;
import org.nutz.lang.Lang;

/**
 * 转换器,也就是用来将字符串转换成队列. TODO 这个类的名字不知道取什么好...
 * 
 * @author juqkai(juqkai@gmail.com)
 * 
 */
public class Converter {
    private static final List<Parse> parses = new ArrayList<Parse>();

    // 表达式字符队列
    private CharQueue exp;
    // 表达式项
    private LinkedList<Object> itemCache;
    // 括号栈
    private LinkedList<BracketType> bracket = new LinkedList<BracketType>();

    // 上一个数据
    private Object prev = null;

    private MethodOpt prem = null;
    private int paramSize = 0;

    public Converter(CharQueue reader) {
        this.exp = reader;
        itemCache = new LinkedList<Object>();
        skipSpace();
        initParse();
    }

    public Converter(String val) {
        this(Lang.inr(val));
    }

    public Converter(Reader reader) {
        this(new CharQueueDefault(reader));
    }

    /**
     * 初始化解析器
     */
    private void initParse() {
        parses.add(new OptParse());
        parses.add(new StringParse());
        parses.add(new IdentifierParse());
        parses.add(new ValParse());
    }

    /**
     * 重新设置解析器
     */
    public void setParse(List<Parse> val) {
        parses.addAll(val);
    }

    /**
     * 初始化EL项
     */
    public void initItems() {
        while (!exp.isEmpty()) {
            Object obj = parseItem();
            // 处理数组的情况
            if (obj.getClass().isArray()) {
                for (Object o : (Object[]) obj) {
                    itemCache.add(o);
                }
                continue;
            }
            itemCache.add(obj);
        }
        itemCache = clearUp(itemCache);
    }

    /**
     * 清理转换后的结果, 主要做一些标识性的转换
     * 
     * @param rpn
     * @return
     */
    private LinkedList<Object> clearUp(LinkedList<Object> rpn) {
        LinkedList<Object> dest = new LinkedList<Object>();
        while (!rpn.isEmpty()) {
            if (!(rpn.peekFirst() instanceof Elobj)) {
                dest.add(rpn.pollFirst());
                continue;
            }
            Elobj obj = (Elobj) rpn.pollFirst();
            // 方法对象
            if (rpn.peekFirst() instanceof MethodOpt) {
                dest.add(new MethodObj(obj.getVal()));
                continue;
            }
            // 属性对象
            if (dest.size() > 0
                && dest.getLast() instanceof AccessOpt
                && rpn.size() > 0
                && rpn.peekFirst() instanceof AccessOpt) {
                dest.add(new FieldObj(obj.getVal()));
                continue;
            }
            // //普通的对象
            // if(!(dest.getLast() instanceof AccessOpt) && !(rpn.peekFirst()
            // instanceof MethodOpt)){
            // continue;
            // }
            dest.add(new IdentifierObj(obj.getVal()));
        }
        return dest;
    }

    /**
     * 解析数据
     */
    private Object parseItem() {
        Object obj = Parse.nullobj;
        for (Parse parse : parses) {
            obj = parse.fetchItem(exp);
            if (obj != Parse.nullobj) {
                skipSpace();
                return parseItem(obj);
            }
        }
        throw new ElException("无法解析!");
    }

    /**
     * 转换数据,主要是转换负号,方法执行
     */
    private Object parseItem(Object item) {
        // 处理参数个数
        if (paramSize == 0) {
            if (!(item instanceof Operator)) {
                paramSize = 1;
            }
        } else {
            if (item instanceof CommaOpt) {
                paramSize++;
            }
        }

        // 左括号
        if (item instanceof LBracketOpt) {
            if (prev instanceof Elobj) {
                prem = new MethodOpt();
                item = new Object[]{prem, new LBracketOpt()};
                paramSize = 0;
                bracket.addFirst(BracketType.Method);
            } else {
                bracket.addFirst(BracketType.Default);
            }
        }

        // 右括号
        if (item instanceof RBracketOpt) {
            switch (bracket.poll()) {
            case Method:
                prem.setSize(paramSize);
                paramSize = -1;
                item = new Object[]{new RBracketOpt(), new InvokeMethodOpt()};
                prem = null;
                break;
            default:
                break;
            }

        }
        // 转换负号'-'
        if (item instanceof SubOpt && NegativeOpt.isNegetive(prev)) {
            item = new NegativeOpt();
        }
        prev = item;
        return item;
    }

    /**
     * 跳过空格,并返回是否跳过空格(是否存在空格)
     */
    private boolean skipSpace() {
        boolean space = false;
        while (!exp.isEmpty() && Character.isWhitespace(exp.peek())) {
            space = true;
            exp.poll();
        }
        return space;
    }

    /**
     * 取得一个有效数据
     */
    public Object fetchItem() {
        return itemCache.poll();
    }

    /**
     * 是否结束
     */
    public boolean isEnd() {
        return itemCache.isEmpty();
    }

    /**
     * 括号类型
     * 
     */
    enum BracketType {
        /**
         * 方法
         */
        Method,
        /**
         * 默认
         */
        Default;
    }
}