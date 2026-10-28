package com.junzhou.service.impl;

import com.junzhou.constant.MessageConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.entity.AddressBook;
import com.junzhou.exception.AddressBookBusinessException;
import com.junzhou.mapper.AddressBookMapper;
import com.junzhou.service.AddressBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;

    /**
     * 条件查询
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        addressBook.setUserId(currentUserId());
        return addressBookMapper.list(addressBook);
    }

    /**
     * 新增地址
     *
     * @param addressBook
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(currentUserId());
        addressBook.setIsDefault(0);
        addressBookMapper.insert(addressBook);
    }

    /**
     * 根据id查询
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        AddressBook addressBook = addressBookMapper.getByIdAndUserId(id, currentUserId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_ACCESSIBLE);
        }
        return addressBook;
    }

    /**
     * 根据id修改地址
     *
     * @param addressBook
     */
    public void update(AddressBook addressBook) {
        getById(addressBook.getId());
        addressBook.setUserId(currentUserId());
        addressBook.setIsDefault(null);
        if (hasEditableFields(addressBook) && addressBookMapper.update(addressBook) != 1) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_ACCESSIBLE);
        }
    }

    /**
     * 设置默认地址
     *
     * @param addressBook
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        AddressBook target = getById(addressBook.getId());
        AddressBook reset = AddressBook.builder().userId(target.getUserId()).isDefault(0).build();
        addressBookMapper.updateIsDefaultByUserId(reset);
        if (addressBookMapper.setDefaultByIdAndUserId(target.getId(), target.getUserId()) != 1) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_ACCESSIBLE);
        }
    }

    /**
     * 根据id删除地址
     *
     * @param id
     */
    public void deleteById(Long id) {
        AddressBook target = getById(id);
        if (addressBookMapper.deleteByIdAndUserId(id, target.getUserId()) != 1) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_NOT_ACCESSIBLE);
        }
    }

    private Long currentUserId() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) {
            throw new AddressBookBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        return userId;
    }

    private boolean hasEditableFields(AddressBook addressBook) {
        return addressBook.getConsignee() != null || addressBook.getSex() != null
                || addressBook.getPhone() != null || addressBook.getProvinceCode() != null
                || addressBook.getProvinceName() != null || addressBook.getCityCode() != null
                || addressBook.getCityName() != null || addressBook.getDistrictCode() != null
                || addressBook.getDistrictName() != null || addressBook.getDetail() != null
                || addressBook.getLabel() != null;
    }
}
