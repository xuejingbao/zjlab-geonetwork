package com.zjlab;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author xue
 * @create 2022-10-27 13:50
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WarnMessage {

    private Position warnPoint;

    private Integer warnRadius;

    private Position nowPoint;

}
