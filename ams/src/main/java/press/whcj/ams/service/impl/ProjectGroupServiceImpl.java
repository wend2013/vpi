package press.whcj.ams.service.impl;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import press.whcj.ams.common.ColumnName;
import press.whcj.ams.common.Constant;
import press.whcj.ams.entity.Project;
import press.whcj.ams.entity.ProjectGroup;
import press.whcj.ams.entity.vo.ProjectGroupVo;
import press.whcj.ams.entity.vo.UserVo;
import press.whcj.ams.exception.ResultCode;
import press.whcj.ams.exception.ServiceException;
import press.whcj.ams.service.ProjectGroupService;
import press.whcj.ams.util.FastUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * @author xyyxhcj@qq.com
 * @since 2019/12/31
 */
@Service
public class ProjectGroupServiceImpl implements ProjectGroupService {
    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public String save(ProjectGroup projectGroupDto, UserVo operator) {
        boolean isUpdate = projectGroupDto.getId() != null;
        String name = projectGroupDto.getName();
        String operatorId = operator.getId();
        FastUtils.checkParams(name);
        ProjectGroup projectGroup;
        if (isUpdate) {
            projectGroup = mongoTemplate.findById(projectGroupDto.getId(), ProjectGroup.class);
            FastUtils.checkNull(projectGroup);
            if (!operatorId.equals(Objects.requireNonNull(projectGroup).getCreateId())) {
                throw new ServiceException(ResultCode.PERMISSION_DENIED);
            }
            projectGroup.setUpdate(null);
        } else {
            projectGroup = new ProjectGroup();
        }
        FastUtils.copyProperties(projectGroupDto, projectGroup);
        if (projectGroupDto.getParentId() != null) {
            // maybe = ''
            projectGroup.setParentId(projectGroupDto.getParentId());
        }
        synchronized (operatorId.intern()) {
            FastUtils.checkNameAndSave(projectGroupDto.getId(), isUpdate, name, projectGroup, mongoTemplate, Criteria.where(ColumnName.CREATE_$ID).is(new ObjectId(operatorId)));
        }
        return projectGroup.getId();
    }

    @Override
    public List<ProjectGroupVo> findList(ProjectGroup projectGroupDto, UserVo operator) {
        Criteria definition;
        if (StringUtils.isEmpty(projectGroupDto.getParentId())) {
            definition = new Criteria();
        } else {
            definition = Criteria.where(ColumnName.PARENT_ID).is(projectGroupDto.getParentId());
        }
        return mongoTemplate.find(new Query(definition), ProjectGroupVo.class, Constant.CollectionName.PROJECT_GROUP);
    }

    @Override
    public List<ProjectGroupVo> findListByParent(ProjectGroup projectGroupDto, UserVo operator) {
        Criteria definition;
        if (StringUtils.isEmpty(projectGroupDto.getParentId())) {
            definition = Criteria.where(ColumnName.PARENT_ID).is(null);
        } else {
            definition = Criteria.where(ColumnName.PARENT_ID).is(projectGroupDto.getParentId());
        }
        return mongoTemplate.find(new Query(definition), ProjectGroupVo.class, Constant.CollectionName.PROJECT_GROUP);
    }

    @Override
    public ProjectGroupVo findDetail(ProjectGroup projectGroup) {
        FastUtils.checkParams(projectGroup.getId());
        return mongoTemplate.findById(projectGroup.getId(), ProjectGroupVo.class, Constant.CollectionName.PROJECT_GROUP);
    }

    @Override
    public void delete(ProjectGroup projectGroup) {
        String projectGroupId = projectGroup.getId();
        FastUtils.checkParams(projectGroupId);
        projectGroup = mongoTemplate.findById(projectGroupId, ProjectGroup.class);
        if (projectGroup == null) {
            return;
        }
        // concat sub & parent
        mongoTemplate.findAndModify(new Query(Criteria.where(ColumnName.PARENT_ID).is(projectGroupId)),
                Update.update(ColumnName.PARENT_ID, projectGroup.getParentId()), ProjectGroup.class);
        mongoTemplate.findAndModify(new Query(Criteria.where(ColumnName.GROUP_ID).is(projectGroupId)),
                Update.update(ColumnName.GROUP_ID, projectGroup.getParentId()), Project.class);
        mongoTemplate.remove(projectGroup);
    }
}
